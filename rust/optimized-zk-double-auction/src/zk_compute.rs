use pbc_zk::*;

use std::vec::Vec;
use create_type_spec_derive::CreateTypeSpec;

const BUYING_ORDER: u8 = 1u8;
const SELLING_ORDER: u8 = 2u8;

#[derive(SecretBinary, Clone, Copy, CreateTypeSpec)]
pub struct SecretOrderInput {
    pub id: Sbi16,
    pub quantity_per_price: [Sbi16; 6],
}

#[derive(SecretBinary, Clone, Copy, CreateTypeSpec)]
pub struct SecretOrderOutput {
    pub id: Sbi16,
    pub quantity: Sbi16,
}

pub struct SecretSellBuyOutputs {
    pub sell_orders: [SecretOrderOutput; 85],
    pub buy_orders: [SecretOrderOutput; 85]
}

fn calculate_supply_demand() -> [[Sbi16; 6]; 2] {
    let mut total_supply = [Sbi16::from(0); 6];
    let mut total_demand = [Sbi16::from(0); 6];
    for var_id in secret_variable_ids()
    {
        let offer = load_sbi::<SecretOrderInput>(var_id);
        if load_metadata::<u8>(var_id) == SELLING_ORDER
        {
            for i in 0usize..6usize
            {
                total_supply[i] = total_supply[i] + offer.quantity_per_price[i];
            }
        }
        else if load_metadata::<u8>(var_id) == BUYING_ORDER
        {
            for i in 0usize..6usize
            {
                total_demand[i] = total_demand[i] + offer.quantity_per_price[i];
            }
        }
    }
    let total_supply_demand = [total_supply, total_demand];
    total_supply_demand
}

fn calculate_market_clearing_price_index(
    total_supply: [Sbi16; 6],
    total_demand: [Sbi16; 6],
) -> Sbi16 {
    let mut min_demand_difference = Sbi16::from(32767);
    let mut market_clearing_price_index = Sbi16::from(0);
    let indicies: [usize; 6] = [0, 5, 1, 4, 2, 3];
    for i in 0usize..6usize
    {
        let index = indicies[i];
        let mut demand_difference: Sbi16 = total_supply[index] - total_demand[index];
        if total_supply[index] < total_demand[index]{
            demand_difference = total_demand[index] - total_supply[index];
        }
        if demand_difference <= min_demand_difference{
            min_demand_difference = demand_difference;
            market_clearing_price_index = Sbi16::from(index as i16);
        }
    }
    market_clearing_price_index
}

fn calculate_sell_buy_order_outputs(
    market_clearing_price_index: Sbi16
) -> SecretSellBuyOutputs {
    let mut buy_order_outputs = [SecretOrderOutput{id:Sbi16::from(0),quantity:Sbi16::from(0)}; 85];
    let mut sell_order_outputs = [SecretOrderOutput{id:Sbi16::from(0),quantity:Sbi16::from(0)}; 85];
    let mut index_s: usize = 0;
    let mut index_b: usize = 0;

    let mut mcp_eq_ary = [Sbu1::from(false); 6];
    for i in 0usize..6usize
    {
        if market_clearing_price_index == Sbi16::from(i as i16)
        {
            mcp_eq_ary[i] = Sbu1::from(true);
        }
    }

    for var_id in secret_variable_ids()
    {
        let offer = load_sbi::<SecretOrderInput>(var_id);
        let mut quantity = Sbi16::from(0);
        if load_metadata::<u8>(var_id) == BUYING_ORDER
        {
            for i in 0usize..6usize
            {
                if mcp_eq_ary[i]{
                    quantity = offer.quantity_per_price[i];
                }
            }
            buy_order_outputs[index_b].id = offer.id;
            buy_order_outputs[index_b].quantity = quantity;
            index_b = index_b + 1;
        }
        if load_metadata::<u8>(var_id) == SELLING_ORDER
        {
            for i in 0usize..6usize
            {
                if mcp_eq_ary[i]{
                    quantity = offer.quantity_per_price[i];
                }
            }
            sell_order_outputs[index_s].id = offer.id;
            sell_order_outputs[index_s].quantity = quantity;
            index_s = index_s + 1;
        }
    }
    SecretSellBuyOutputs {
        sell_orders: sell_order_outputs,
        buy_orders: buy_order_outputs
    }
}

#[zk_compute(shortname = 0x62)]
pub fn double_auction() -> (
    Sbi16,
    [SecretOrderOutput; 85],
    [SecretOrderOutput; 85],)
{
    let total_supply_demand = calculate_supply_demand();

    let market_clearing_price_index 
        = calculate_market_clearing_price_index(total_supply_demand[0], total_supply_demand[1]);

    let sell_buy_outputs 
        = calculate_sell_buy_order_outputs(market_clearing_price_index);
    
    (market_clearing_price_index, sell_buy_outputs.sell_orders, sell_buy_outputs.buy_orders)
}