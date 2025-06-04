#[macro_use]
extern crate pbc_contract_codegen;
extern crate pbc_contract_common;

use pbc_contract_common::address::Address;
use create_type_spec_derive::CreateTypeSpec;
use pbc_contract_common::context::ContractContext;
use pbc_contract_common::events::EventGroup;
use read_write_state_derive::ReadWriteState;
use read_write_rpc_derive::ReadRPC;
use read_write_rpc_derive::WriteRPC;
use std::vec::Vec;

#[derive(ReadWriteState, CreateTypeSpec, Clone)]
pub struct Trade {
    pub buyer_id: i16,
    pub seller_id: i16,
    pub quantity: i16,
}

impl Trade
{
    pub fn new_trade (
        buyer_id: i16, 
        seller_id: i16, 
        quantity: i16
    ) -> Self
    {
        Self{
            buyer_id,
            seller_id,
            quantity
        }
    }
}

#[derive(ReadWriteState, Clone, Copy, CreateTypeSpec, ReadRPC, WriteRPC)]
pub struct OrderInput {
    pub id: i16,
    pub quantity_per_price: [i16; 6],
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
    pub sell_orders: Vec<Order>,
    pub buy_orders: Vec<Order>,
    pub sell_order_inputs: Vec<OrderInput>,
    pub buy_order_inputs: Vec<OrderInput>,
}

#[init]
fn initialize(
    ctx: ContractContext
) -> ContractState {
    ContractState {
        auction_holder: ctx.sender,
        prices: [0; 6],
        market_clearing_price_index: None,
        matched_orders: Vec::<Trade>::new(),
        sell_orders: Vec::<Order>::new(),
        buy_orders: Vec::<Order>::new(),
        sell_order_inputs: Vec::<OrderInput>::new(),
        buy_order_inputs: Vec::<OrderInput>::new(),
    }
}

#[action(shortname = 0x00)]
fn reset_contract(
    ctx: ContractContext,
    state: ContractState,
) -> (ContractState, Vec<EventGroup>) {
    assert_eq!(state.auction_holder, ctx.sender, "Only the auction holder can reset!");
    assert_ne!(state.market_clearing_price_index.is_none(), true, "Cannot reset the contract before an auction!");

    let new_state = ContractState {
        auction_holder: ctx.sender,
        prices: [0; 6],
        market_clearing_price_index: None,
        matched_orders: vec![],
        sell_orders: vec![],
        buy_orders: vec![],
        sell_order_inputs: vec![],
        buy_order_inputs: vec![],
    };
    (
        new_state,
        vec![],
    )
}

#[action(shortname = 0x47)]
fn update_prices (
    ctx: ContractContext, 
    mut state: ContractState,
    min: i16,
    max: i16
) -> (ContractState, Vec<EventGroup>)
{
    assert_eq!(state.auction_holder, ctx.sender, "Only the auction holder can update the prices!");

    let price_step: i16 = (max - min) / 5;
    for i in 0usize..6usize
    {
        state.prices[i] = min + (price_step * i as i16);
    }
    (state, vec![])
}

#[action(shortname = 0x40)]
fn input_buy_order(
    _ctx: ContractContext,
    mut state: ContractState,
    buy_order: OrderInput,
) -> (
    ContractState,
    Vec<EventGroup>,
) {
    state.buy_order_inputs.push(buy_order);
    (state, vec![])
}

#[action(shortname = 0x45)]
fn input_sell_order(
    _ctx: ContractContext,
    mut state: ContractState,
    sell_order: OrderInput,
) -> (
    ContractState,
    Vec<EventGroup>,
) {
    state.sell_order_inputs.push(sell_order);
    (state, vec![])
}

#[action(shortname = 0x02)]
fn hold_double_auction(
    ctx: ContractContext,
    state: ContractState,
) -> (ContractState, Vec<EventGroup>) {

    assert_eq!(state.auction_holder, ctx.sender, "Only the auction holder can hold the auction!");

    double_auction(
        ctx,
        state
    )
}

#[action]
fn save_opened_variable(
    _ctx: ContractContext,
    mut state: ContractState,
) -> (ContractState, Vec<EventGroup>) {

    state.matched_orders = vec![];

    let mut sell_orders: Vec<Order> = state.sell_orders.clone();
    let mut buy_orders: Vec<Order> = state.buy_orders.clone();
    sell_orders.reverse();
    buy_orders.reverse();

    let mut sell_order_index: usize = 0;
    let mut buy_order_index: usize = 0;
    
    while sell_order_index < sell_orders.len() && buy_order_index < buy_orders.len()
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
    (
        state,
        vec![]
    )
}
fn aggregate_sell_orders(
    sell_order_inputs: &Vec<OrderInput>,
) -> Vec<OrderInput> {
    let mut sell_orders: Vec<OrderInput> = vec![];

    for supply in sell_order_inputs.iter()
    {
        let mut sell_order = OrderInput {
            id: supply.id,
            quantity_per_price: supply.quantity_per_price,
        };
        let mut supply_aggregate = 0;
        for i in 0usize..6usize
        {
            sell_order.quantity_per_price[i] = sell_order.quantity_per_price[i] + supply_aggregate;
            supply_aggregate = sell_order.quantity_per_price[i];
        }
        sell_orders.push(sell_order);
    }
    sell_orders
}

fn aggregate_buy_orders(
    buy_order_inputs: &Vec<OrderInput>,
) -> Vec<OrderInput> {
    let mut buy_orders: Vec<OrderInput> = vec![];

    for demand in buy_order_inputs.iter()
    {
        let mut buy_order = OrderInput {
            id: demand.id,
            quantity_per_price: demand.quantity_per_price,
        };
        let mut demand_aggregate = 0;
        for i in 0usize..6usize
        {
            buy_order.quantity_per_price[5-i] = buy_order.quantity_per_price[5-i] + demand_aggregate;
            demand_aggregate = buy_order.quantity_per_price[5-i];
        }
        buy_orders.push(buy_order);
    }
    buy_orders
}

fn calculate_total_supply(
    sell_order_inputs: &[OrderInput],
) -> [i16; 6] {
    let mut total_supply = [0; 6];

    for supply in sell_order_inputs.iter()
    {
        for i in 0usize..6usize
        {
            total_supply[i] = total_supply[i] + supply.quantity_per_price[i];
        }
    }
    total_supply
}

fn calculate_total_demand(
    buy_order_inputs: &[OrderInput],
) -> [i16; 6] {
    let mut total_demand = [0; 6];

    for demand in buy_order_inputs.iter()
    {
        for i in 0usize..6usize
        {
            total_demand[i] = total_demand[i] + demand.quantity_per_price[i];
        }
    }
    total_demand
}

fn calculate_market_clearing_price_index(
    total_supply: [i16; 6],
    total_demand: [i16; 6],
) -> i16 {
    let mut min_demand_difference: i16 = i16::MAX;
    let mut market_clearing_price_index: i16 = 0;
    for i in [3, 2, 4, 1, 5, 0]
    {
        let mut demand_difference = total_supply[i] - total_demand[i];
        demand_difference = demand_difference.abs();
        if demand_difference < min_demand_difference
        {
            min_demand_difference = demand_difference;
            market_clearing_price_index = i as i16;
        }
    }
    market_clearing_price_index
}

fn calculate_sell_order_outputs(
    sell_orders: &Vec<OrderInput>,
    market_clearing_price_index: i16,
) -> Vec<Order> {
    let mut sell_order_outputs = vec![];

    for offer in sell_orders.iter().rev()
    {
        let mut quantity = 0;

        for i in 0usize..6usize
        {
            if market_clearing_price_index == (i as i16) {
                quantity = offer.quantity_per_price[i];
            }
        }
        if quantity > 0
        {
            let order = Order {
                id: offer.id,
                quantity,
            };
            sell_order_outputs.push(order);
        }
    }
    sell_order_outputs
}

fn calculate_buy_order_outputs(
    buy_orders: &Vec<OrderInput>,
    market_clearing_price_index: i16,
) -> Vec<Order> {
    let mut buy_order_outputs = vec![];

    for offer in buy_orders.iter().rev()
    {
        let mut quantity = 0;

        for i in 0usize..6usize
        {
            if market_clearing_price_index == (i as i16) {
                quantity = offer.quantity_per_price[i];
            }
        }
        if quantity > 0
        {
            let order = Order {
                id: offer.id,
                quantity,
            };
            buy_order_outputs.push(order);
        }
    }
    buy_order_outputs
}

fn double_auction(
    ctx: ContractContext,
    mut state: ContractState
) -> (
    ContractState,
    Vec<EventGroup>)
{
    let sell_orders = aggregate_sell_orders(&state.sell_order_inputs);
    let buy_orders = aggregate_buy_orders(&state.buy_order_inputs);

    let total_supply = calculate_total_supply(&sell_orders);
    let total_demand = calculate_total_demand(&buy_orders);

    state.market_clearing_price_index = 
        Some(calculate_market_clearing_price_index(
            total_supply,
            total_demand,
        ));

    state.sell_orders = calculate_sell_order_outputs(
            &sell_orders,
            state.market_clearing_price_index.unwrap(),
        );
    state.buy_orders = calculate_buy_order_outputs(
            &buy_orders,
            state.market_clearing_price_index.unwrap(),
        );

    save_opened_variable(
        ctx,
        state
    )
}