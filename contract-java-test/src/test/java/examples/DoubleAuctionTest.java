package examples;

import com.partisiablockchain.BlockchainAddress;
import com.partisiablockchain.language.abicodegen.DoubleAuction;
import com.partisiablockchain.language.abicodegen.DoubleAuction.OrderInput;
import com.partisiablockchain.language.junit.ContractBytes;
import com.partisiablockchain.language.junit.ContractTest;
import com.partisiablockchain.language.junit.JunitContractTest;
import com.partisiablockchain.language.junit.exceptions.ActionFailureException;
import org.assertj.core.api.Assertions;

import java.nio.file.Path;
import java.util.List;

public final class DoubleAuctionTest extends JunitContractTest{
    private static final ContractBytes double_auction_BYTES = 
          ContractBytes.fromPbcFile(
          Path.of("../rust/target/wasm32-unknown-unknown/release/double_auction.pbc"),
          Path.of("../rust/target/wasm32-unknown-unknown/release/double_auction_runner"));

    private BlockchainAddress household1;
    private BlockchainAddress household2;
    private BlockchainAddress household3;
    private BlockchainAddress household4;
    private BlockchainAddress household5;
    private BlockchainAddress household6;
    private BlockchainAddress double_auction_contract;

    @ContractTest
    void shouldDeploy() {
        // Arrange
        household1 = blockchain.newAccount(2);
        household2 = blockchain.newAccount(3);
        household3 = blockchain.newAccount(4);
        household4 = blockchain.newAccount(5);
        household5 = blockchain.newAccount(6);
        household6 = blockchain.newAccount(7);

        byte[] initRpc = DoubleAuction.initialize();

        // Act
        double_auction_contract = blockchain.deployContract(household1, double_auction_BYTES, initRpc);

        // Assert
        Assertions.assertThat(double_auction_contract).isNotNull();

        DoubleAuction.ContractState state = 
            DoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state).isNotNull();
        Assertions.assertThat(state.marketClearingPriceIndex()).isNull();
        Assertions.assertThat(state.matchedOrders()).isEmpty();
        Assertions.assertThat(state.prices().size()).isEqualTo(6);
        Assertions.assertThat(state.prices().get(0)).isEqualTo((short)0);
    }

    @ContractTest(previous = "shouldDeploy")
    void shouldSetPrices() {
        // Arrange
        short min = 40; // Price in danish penny
        short max = 58; // Price in danish penny
        byte[] setPrices = DoubleAuction.updatePrices(min, max);

        // Act
        blockchain.sendAction(household1, double_auction_contract, setPrices);

        // Assert
        DoubleAuction.ContractState state = 
            DoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.prices().size()).isEqualTo(6);
        Assertions.assertThat(state.prices().get(0)).isEqualTo((short)40);
        Assertions.assertThat(state.prices().get(1)).isEqualTo((short)43);
        Assertions.assertThat(state.prices().get(2)).isEqualTo((short)46);
        Assertions.assertThat(state.prices().get(3)).isEqualTo((short)49);
        Assertions.assertThat(state.prices().get(4)).isEqualTo((short)52);
        Assertions.assertThat(state.prices().get(5)).isEqualTo((short)55);
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldHaveMarketClearingPriceIndexInTheMiddle(){
        // Arrange
        OrderInput sell_order1 = new OrderInput((short)1, List.of((short)0, (short)0, (short)5, (short)5, (short)10, (short)10));
        OrderInput sell_order2 = new OrderInput((short)2, List.of((short)0, (short)0, (short)0, (short)0, (short)0, (short)500));
        OrderInput sell_order3 = new OrderInput((short)3, List.of((short)5, (short)6, (short)7, (short)8, (short)9, (short)10));

        OrderInput buy_order1 = new OrderInput((short)4, List.of((short)500, (short)0, (short)0, (short)0, (short)0, (short)0));
        OrderInput buy_order2 = new OrderInput((short)5, List.of((short)15, (short)15, (short)3, (short)0, (short)0, (short)0));
        OrderInput buy_order3 = new OrderInput((short)6, List.of((short)10, (short)9, (short)8, (short)7, (short)6, (short)5));

        // Act
        blockchain.sendAction(household1, double_auction_contract, DoubleAuction.inputSellOrder(sell_order1)); 
        blockchain.sendAction(household2, double_auction_contract, DoubleAuction.inputSellOrder(sell_order2)); 
        blockchain.sendAction(household3, double_auction_contract, DoubleAuction.inputSellOrder(sell_order3)); 
        blockchain.sendAction(household4, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order1)); 
        blockchain.sendAction(household5, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order2)); 
        blockchain.sendAction(household6, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order3)); 

        byte[] holdDoubleAuction = DoubleAuction.holdDoubleAuction();

        blockchain.sendAction(household1, double_auction_contract, holdDoubleAuction);

        // Assert
        DoubleAuction.ContractState state = 
            DoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.marketClearingPriceIndex()).isEqualTo((short)2);
        Assertions.assertThat(state.matchedOrders().size()).isEqualTo((short)3);
        Assertions.assertThat(state.matchedOrders().get(0)).isEqualTo(new DoubleAuction.Trade((short)5, (short)1, (short)3));
        Assertions.assertThat(state.matchedOrders().get(1)).isEqualTo(new DoubleAuction.Trade((short)6, (short)1, (short)2));
        Assertions.assertThat(state.matchedOrders().get(2)).isEqualTo(new DoubleAuction.Trade((short)6, (short)3, (short)18));
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldAggregateOrders(){
        // Arrange
        OrderInput sell_order1 = new OrderInput((short)1, List.of((short)0, (short)0, (short)50, (short)0, (short)0, (short)0));
        OrderInput sell_order2 = new OrderInput((short)2, List.of((short)0, (short)0, (short)0, (short)0, (short)0, (short)0));

        OrderInput buy_order1 = new OrderInput((short)3, List.of((short)0, (short)0, (short)0, (short)100, (short)0, (short)0));
        OrderInput buy_order2 = new OrderInput((short)4, List.of((short)0, (short)0, (short)0, (short)0, (short)0, (short)0));

        // Act
        blockchain.sendAction(household1, double_auction_contract, DoubleAuction.inputSellOrder(sell_order1)); 
        blockchain.sendAction(household2, double_auction_contract, DoubleAuction.inputSellOrder(sell_order2)); 
        blockchain.sendAction(household3, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order1)); 
        blockchain.sendAction(household4, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order2)); 

        byte[] holdDoubleAuction = DoubleAuction.holdDoubleAuction();

        blockchain.sendAction(household1, double_auction_contract, holdDoubleAuction);

        // Assert
        DoubleAuction.ContractState state = 
            DoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.marketClearingPriceIndex()).isEqualTo((short)3);
        Assertions.assertThat(state.matchedOrders().size()).isEqualTo((short)1);
        Assertions.assertThat(state.matchedOrders().get(0)).isEqualTo(new DoubleAuction.Trade((short)3, (short)1, (short)50));
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldHaveMarketClearingPriceIndexOnTheLeft(){
        // Arrange
        OrderInput sell_order1 = new OrderInput((short)1, List.of((short)0, (short)0, (short)5, (short)5, (short)10, (short)10));
        OrderInput sell_order2 = new OrderInput((short)2, List.of((short)0, (short)0, (short)0, (short)0, (short)0, (short)500));
        OrderInput sell_order3 = new OrderInput((short)3, List.of((short)5, (short)6, (short)7, (short)8, (short)9, (short)10));

        OrderInput buy_order1 = new OrderInput((short)4, List.of((short)5, (short)0, (short)0, (short)0, (short)0, (short)0));
        OrderInput buy_order2 = new OrderInput((short)5, List.of((short)0, (short)0, (short)0, (short)0, (short)0, (short)0));
        OrderInput buy_order3 = new OrderInput((short)6, List.of((short)0, (short)0, (short)0, (short)0, (short)0, (short)0));

        // Act
        blockchain.sendAction(household1, double_auction_contract, DoubleAuction.inputSellOrder(sell_order1)); 
        blockchain.sendAction(household2, double_auction_contract, DoubleAuction.inputSellOrder(sell_order2)); 
        blockchain.sendAction(household3, double_auction_contract, DoubleAuction.inputSellOrder(sell_order3)); 
        blockchain.sendAction(household4, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order1)); 
        blockchain.sendAction(household5, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order2)); 
        blockchain.sendAction(household6, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order3)); 

        byte[] holdDoubleAuction = DoubleAuction.holdDoubleAuction();

        blockchain.sendAction(household1, double_auction_contract, holdDoubleAuction);

        // Assert
        DoubleAuction.ContractState state = 
            DoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.marketClearingPriceIndex()).isEqualTo((short)0);
        Assertions.assertThat(state.matchedOrders().size()).isEqualTo(1);
        Assertions.assertThat(state.matchedOrders().get(0)).isEqualTo(new DoubleAuction.Trade((short)4,(short)3,(short)5));
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldReturnNoMatchedOrders(){
        // Arrange
        OrderInput sell_order1 = new OrderInput((short)1, List.of((short)0, (short)0, (short)5, (short)5, (short)10, (short)10));
        OrderInput sell_order2 = new OrderInput((short)2, List.of((short)0, (short)0, (short)0, (short)0, (short)0, (short)500));
        OrderInput sell_order3 = new OrderInput((short)3, List.of((short)5, (short)6, (short)7, (short)8, (short)9, (short)10));

        OrderInput buy_order1 = new OrderInput((short)4, List.of((short)5, (short)0, (short)0, (short)0, (short)0, (short)0));
        OrderInput buy_order2 = new OrderInput((short)5, List.of((short)15, (short)0, (short)0, (short)0, (short)0, (short)0));
        OrderInput buy_order3 = new OrderInput((short)6, List.of((short)10, (short)0, (short)0, (short)0, (short)0, (short)0));

        // Act
        blockchain.sendAction(household1, double_auction_contract, DoubleAuction.inputSellOrder(sell_order1)); 
        blockchain.sendAction(household2, double_auction_contract, DoubleAuction.inputSellOrder(sell_order2)); 
        blockchain.sendAction(household3, double_auction_contract, DoubleAuction.inputSellOrder(sell_order3)); 
        blockchain.sendAction(household4, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order1)); 
        blockchain.sendAction(household5, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order2)); 
        blockchain.sendAction(household6, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order3)); 

        byte[] holdDoubleAuction = DoubleAuction.holdDoubleAuction();

        blockchain.sendAction(household1, double_auction_contract, holdDoubleAuction);

        // Assert
        DoubleAuction.ContractState state = 
            DoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.marketClearingPriceIndex()).isEqualTo((short)1);
        Assertions.assertThat(state.matchedOrders().size()).isEqualTo((short)0);
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldUpdatePrices()
    {
        // Arrange
        short min = 48; // Price in danish penny
        short max = 69; // Price in danish penny
        byte[] updatePrices = DoubleAuction.updatePrices(min, max);

        // Act
        blockchain.sendAction(household1, double_auction_contract, updatePrices);

        // Assert
        DoubleAuction.ContractState state = 
            DoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.prices().size()).isEqualTo(6);
        Assertions.assertThat(state.prices().get(0)).isEqualTo((short)48);
        Assertions.assertThat(state.prices().get(1)).isEqualTo((short)52);
        Assertions.assertThat(state.prices().get(2)).isEqualTo((short)56);
        Assertions.assertThat(state.prices().get(3)).isEqualTo((short)60);
        Assertions.assertThat(state.prices().get(4)).isEqualTo((short)64);
        Assertions.assertThat(state.prices().get(5)).isEqualTo((short)68);
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldFailToUpdatePricesActNotContractOwner()
    {
        // Arrange
        byte[] updatePrices = DoubleAuction.updatePrices((short)0, (short)0);

        // Act & Assert
        Assertions.assertThatThrownBy(
            () -> blockchain.sendAction(household2, double_auction_contract, updatePrices))
        .isInstanceOf(ActionFailureException.class)
        .hasMessageContaining("Only the auction holder can update the prices!");
    }

    @ContractTest(previous = "shouldHaveMarketClearingPriceIndexOnTheLeft")
    void shouldResetContract()
    {
        // Arrange
        byte[] reset = DoubleAuction.resetContract();

        // Act
        blockchain.sendAction(household1, double_auction_contract, reset);

        // Assert
        DoubleAuction.ContractState state = 
            DoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.marketClearingPriceIndex()).isNull();
        Assertions.assertThat(state.matchedOrders()).isEmpty();
        Assertions.assertThat(state.prices().size()).isEqualTo(6);
        Assertions.assertThat(state.prices().get(0)).isEqualTo((short)0);
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldFailToResetContractBeforeAnAuctionIsHeld()
    {
        // Act
        byte[] reset = DoubleAuction.resetContract();

        // Arrange
        Assertions.assertThatThrownBy(
            () -> blockchain.sendAction(household1, double_auction_contract, reset))
        .isInstanceOf(ActionFailureException.class)
        .hasMessageContaining("Cannot reset the contract before an auction!");
    }

    @ContractTest(previous = "shouldResetContract")
    void shouldUpdatePricesAfterReset() {
        // Arrange
        short min = 55; // Price in danish penny
        short max = 72; // Price in danish penny
        byte[] setPrices = DoubleAuction.updatePrices(min, max);

        // Act
        blockchain.sendAction(household1, double_auction_contract, setPrices);

        // Assert
        DoubleAuction.ContractState state = 
            DoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));
        
        Assertions.assertThat(state.prices().size()).isEqualTo(6);
        Assertions.assertThat(state.prices().get(0)).isEqualTo((short)55);
        Assertions.assertThat(state.prices().get(1)).isEqualTo((short)58);
        Assertions.assertThat(state.prices().get(2)).isEqualTo((short)61);
        Assertions.assertThat(state.prices().get(3)).isEqualTo((short)64);
        Assertions.assertThat(state.prices().get(4)).isEqualTo((short)67);
        Assertions.assertThat(state.prices().get(5)).isEqualTo((short)70);
    }

    @ContractTest(previous = "shouldUpdatePricesAfterReset")
    void shouldHaveMarketClearingPriceIndexInTheMiddleAfterResetAndUpdatePrices() {
        // Arrange
        OrderInput sell_order1 = new OrderInput((short)1, List.of((short)0, (short)2, (short)4, (short)6, (short)8, (short)10));
        OrderInput sell_order2 = new OrderInput((short)2, List.of((short)0, (short)0, (short)0, (short)0, (short)0, (short)50));

        OrderInput buy_order1 = new OrderInput((short)3, List.of((short)10, (short)8, (short)6, (short)4, (short)2, (short)0));
        OrderInput buy_order2 = new OrderInput((short)4, List.of((short)3, (short)3, (short)3, (short)2, (short)0, (short)0));
        OrderInput buy_order3 = new OrderInput((short)5, List.of((short)0, (short)0, (short)0, (short)0, (short)0, (short)0));
        OrderInput buy_order4 = new OrderInput((short)6, List.of((short)0, (short)0, (short)0, (short)0, (short)0, (short)0));

        // Act
        blockchain.sendAction(household1, double_auction_contract, DoubleAuction.inputSellOrder(sell_order1)); 
        blockchain.sendAction(household2, double_auction_contract, DoubleAuction.inputSellOrder(sell_order2)); 
        blockchain.sendAction(household3, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order1)); 
        blockchain.sendAction(household4, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order2)); 
        blockchain.sendAction(household5, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order3)); 
        blockchain.sendAction(household6, double_auction_contract, DoubleAuction.inputBuyOrder(buy_order4)); 

        byte[] holdDoubleAuction = DoubleAuction.holdDoubleAuction();

        blockchain.sendAction(household1, double_auction_contract, holdDoubleAuction);

        // Assert
        DoubleAuction.ContractState state = 
            DoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.marketClearingPriceIndex()).isEqualTo((short)3);
        Assertions.assertThat(state.matchedOrders().size()).isEqualTo(2);
        Assertions.assertThat(state.matchedOrders().get(0)).isEqualTo(new DoubleAuction.Trade((short)3, (short)1, (short)6));
        Assertions.assertThat(state.matchedOrders().get(1)).isEqualTo(new DoubleAuction.Trade((short)4, (short)1, (short)2));
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldFailToHoldDoubleAuctionActNotContractOwner()
    {
        // Arrange
        byte[] holdDoubleAuction = DoubleAuction.holdDoubleAuction();

        // Act & Assert
        Assertions.assertThatThrownBy(
            () -> blockchain.sendAction(household2, double_auction_contract, holdDoubleAuction))
        .isInstanceOf(ActionFailureException.class)
        .hasMessageContaining("Only the auction holder can hold the auction!");
    }
}