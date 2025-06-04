#[macro_use]
extern crate pbc_contract_codegen;
extern crate pbc_contract_common;

use pbc_contract_common::address::Address;
use create_type_spec_derive::CreateTypeSpec;
use pbc_contract_common::context::ContractContext;
use pbc_contract_common::events::EventGroup;
use pbc_contract_common::zk::{SecretVarId, ZkInputDef, ZkState, ZkStateChange};
use read_write_state_derive::ReadWriteState;
use pbc_traits::ReadWriteState;
use std::vec::Vec;

mod zk_compute;

use zk_compute::SecretOrderInput;

const NUM_OF_SELL_ORDERS: usize = 85;
const NUM_OF_BUY_ORDERS: usize = 85;

#[derive(ReadWriteState, Debug)]
#[repr(C)]
struct SecretInputVarMetadata {
    order_type: SecretInputVarType,
}

#[derive(ReadWriteState, Debug, PartialEq)]
#[repr(u8)]
enum SecretInputVarType {
    Buying = 1,
    Selling = 2,
    Matched = 3,
}

#[derive(ReadWriteState, CreateTypeSpec, Clone)]
pub struct Trade {
    pub buyer_id: i16,
    pub seller_id: i16,
    pub quantity: i16,
}

impl Trade
{
    pub fn new_trade (
        buyer_id:i16, 
        seller_id:i16, 
        quantity:i16
    ) -> Self
    {
        Self{
            buyer_id,
            seller_id,
            quantity
        }
    }
}

#[derive(ReadWriteState, CreateTypeSpec, Clone, Copy)]
pub struct Order {
    pub id: i16,
    pub quantity: i16,
}

#[state]
struct ContractState {
    pub auction_holder: Address,
    pub prices: [i16; 6],
    pub market_clearing_price_index: Option<i16>,
    pub matched_orders: Vec<Trade>,
}

#[init(zk = true)]
fn initialize(ctx: ContractContext, _zk_state: ZkState<SecretInputVarMetadata>) -> ContractState {
    ContractState {
        auction_holder: ctx.sender,
        prices: [0; 6],
        market_clearing_price_index: None,
        matched_orders: vec![],
    }
}

#[action(shortname = 0x00, zk = true)]
fn reset_contract(
    ctx: ContractContext,
    state: ContractState,
    zk_state: ZkState<SecretInputVarMetadata>,
) -> (ContractState, Vec<EventGroup>, Vec<ZkStateChange>) {
    assert_eq!(state.auction_holder, ctx.sender, "Only the auction holder can reset!");
    assert_ne!(state.market_clearing_price_index.is_none(), true, "Cannot reset the contract before an auction!");

    let new_state = ContractState {
        auction_holder: ctx.sender,
        prices: [0; 6],
        market_clearing_price_index: None,
        matched_orders: vec![],
    };

    let all_variables = zk_state
        .secret_variables
        .iter()
        .chain(zk_state.pending_inputs.iter())
        .map(|(v, _)| v)
        .collect();

    (
        new_state,
        vec![],
        vec![ZkStateChange::DeleteVariables {
            variables_to_delete: all_variables,
        }],
    )
}

#[action(shortname = 0x47, zk = true)]
fn update_prices (
    ctx: ContractContext, 
    mut state: ContractState,
    _zk_state: ZkState<SecretInputVarMetadata>, 
    min: i16,
    max: i16
) -> (ContractState, Vec<EventGroup>, Vec<ZkStateChange>)
{
    assert_eq!(state.auction_holder, ctx.sender, "Only the auction holder can update the prices!");

    let price_step: i16 = (max - min) / 5;
    for i in 0usize..6usize
    {
        state.prices[i] = min + (price_step * i as i16);
    }
    (state, vec![], vec![])
}

#[zk_on_secret_input(shortname = 0x40, secret_type = "SecretOrderInput")]
fn secret_input_buy_order(
    _ctx: ContractContext,
    state: ContractState,
    _zk_state: ZkState<SecretInputVarMetadata>,
) -> (
    ContractState,
    Vec<EventGroup>,
    ZkInputDef<SecretInputVarMetadata, SecretOrderInput>,
) {
    let input_def =
        ZkInputDef::with_metadata(None, SecretInputVarMetadata{order_type:SecretInputVarType::Buying,});

    (state, vec![], input_def)
}

#[zk_on_secret_input(shortname = 0x45, secret_type = "SecretOrderInput")]
fn secret_input_sell_order(
    _ctx: ContractContext,
    state: ContractState,
    _zk_state: ZkState<SecretInputVarMetadata>,
) -> (
    ContractState,
    Vec<EventGroup>,
    ZkInputDef<SecretInputVarMetadata, SecretOrderInput>,
) {
    let input_def =
        ZkInputDef::with_metadata(
            None, 
            SecretInputVarMetadata{
                order_type: SecretInputVarType::Selling,
            });

    (state, vec![], input_def)
}

#[action(shortname = 0x02, zk = true)]
fn hold_double_auction(
    ctx: ContractContext,
    state: ContractState,
    _zk_state: ZkState<SecretInputVarMetadata>,
) -> (ContractState, Vec<EventGroup>, Vec<ZkStateChange>) {

    assert_eq!(state.auction_holder, ctx.sender, "Only the auction holder can hold the auction!");

    (
        state,
        vec![],
        vec![zk_compute::double_auction_start(
            Some(SHORTNAME_COMPUTATION_COMPLETE),
            [
                &SecretInputVarMetadata{order_type: SecretInputVarType::Matched,},
                &SecretInputVarMetadata{order_type: SecretInputVarType::Matched,},
                &SecretInputVarMetadata{order_type: SecretInputVarType::Matched,},
            ],
        )],
    )
}

#[zk_on_compute_complete(shortname = 0x42)]
fn computation_complete(
    _ctx: ContractContext,
    state: ContractState,
    _zk_state: ZkState<SecretInputVarMetadata>,
    output_variables: Vec<SecretVarId>,
) -> (ContractState, Vec<EventGroup>, Vec<ZkStateChange>) {
    (
        state,
        vec![],
        vec![ZkStateChange::OpenVariables {
            variables: output_variables,
        }],
    )
}

#[zk_on_variables_opened]
fn save_opened_variable(
    _ctx: ContractContext,
    mut state: ContractState,
    zk_state: ZkState<SecretInputVarMetadata>,
    opened_variables: Vec<SecretVarId>,
) -> (ContractState, Vec<EventGroup>, Vec<ZkStateChange>) {
    
    assert!(
        opened_variables.len() == 3,
        "Unexpected number of output variables"
    );

    state.matched_orders = vec![];

    let eq_price: i16 = read_variable(&zk_state,opened_variables.first());
    state.market_clearing_price_index = Some(eq_price); 

    let mut sell_orders: [Order; NUM_OF_SELL_ORDERS] = read_variable(&zk_state, opened_variables.get(1));
    let mut buy_orders: [Order; NUM_OF_BUY_ORDERS] = read_variable(&zk_state, opened_variables.get(2));

    let mut sell_order_index: usize = 0;
    let mut buy_order_index: usize = 0;

    while sell_order_index < NUM_OF_SELL_ORDERS && buy_order_index < NUM_OF_BUY_ORDERS
    {
        let seller: Order = sell_orders[sell_order_index];
        let buyer: Order = buy_orders[buy_order_index];
        let mut trade: Trade = Trade::new_trade(
            buyer.id,
            seller.id,
            0,
        );
        if seller.quantity == buyer.quantity {
            sell_order_index = sell_order_index + 1;
            buy_order_index = buy_order_index + 1;
            trade.quantity = buyer.quantity;
            if buyer.quantity > 0
            {
                state.matched_orders.push(trade);
            }
        }
        else if seller.quantity < buyer.quantity
        {
            sell_order_index = sell_order_index + 1;
            buy_orders[buy_order_index].quantity = buyer.quantity - seller.quantity;
            trade.quantity = seller.quantity;
            if seller.quantity > 0
            {
                state.matched_orders.push(trade);
            }
        }
        else if seller.quantity > buyer.quantity
        {
            buy_order_index = buy_order_index + 1;
            sell_orders[sell_order_index].quantity = seller.quantity - buyer.quantity;
            trade.quantity = buyer.quantity;
            if buyer.quantity > 0
            {
                state.matched_orders.push(trade);
            }
        }
    } 
    (state, vec![], vec![])
}

fn read_variable<T: ReadWriteState>(
    zk_state: &ZkState<SecretInputVarMetadata>,
    variable_id: Option<&SecretVarId>,
) -> T {
    let variable_id = *variable_id.unwrap();
    let variable = zk_state.get_variable(variable_id).unwrap();
    let buffer: Vec<u8> = variable.data.clone().unwrap();
    let result = T::state_read_from(&mut buffer.as_slice());
    result
}
