package examples;

import com.partisiablockchain.BlockchainAddress;
import com.partisiablockchain.language.abicodegen.OptimizedZkDoubleAuction;
import com.partisiablockchain.language.abicodegen.OptimizedZkDoubleAuction.Trade;
import com.partisiablockchain.language.junit.ContractBytes;
import com.partisiablockchain.language.junit.ContractTest;
import com.partisiablockchain.language.junit.JunitContractTest;
import com.partisiablockchain.language.junit.exceptions.ActionFailureException;
import com.secata.stream.CompactBitArray;
import com.secata.stream.BitOutput;
import org.assertj.core.api.Assertions;

import java.nio.file.Path;
import java.util.List;

public final class OptimizedZkDoubleAuctionTest extends JunitContractTest{
    private static final ContractBytes ZK_DOUBLE_AUCTION_BYTES = 
          ContractBytes.fromPaths(
          Path.of("../rust/target/wasm32-unknown-unknown/release/optimized_zk_double_auction.zkwa"),
          Path.of("../rust/target/wasm32-unknown-unknown/release/optimized_zk_double_auction.abi"),
          Path.of("../rust/target/wasm32-unknown-unknown/release/optimized_zk_double_auction_runner"));

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

        byte[] initRpc = OptimizedZkDoubleAuction.initialize();

        // Act
        double_auction_contract = blockchain.deployZkContract(household1, ZK_DOUBLE_AUCTION_BYTES, initRpc);

        // Assert
        OptimizedZkDoubleAuction.ContractState state = 
            OptimizedZkDoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

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
        byte[] setPrices = OptimizedZkDoubleAuction.updatePrices(min, max);

        // Act
        blockchain.sendAction(household1, double_auction_contract, setPrices);

        // Assert
        OptimizedZkDoubleAuction.ContractState state = 
            OptimizedZkDoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

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
        Order sell_order1 = new Order(1, List.of(0, 0, 5, 5, 10, 10));
        Order sell_order2 = new Order(2, List.of(0, 0, 0, 0, 0, 500));
        Order sell_order3 = new Order(3, List.of(5, 6, 7, 8, 9, 10));

        Order buy_order1 = new Order(4, List.of(500, 0, 0, 0, 0, 0));
        Order buy_order2 = new Order(5, List.of(15,15, 3, 0, 0, 0));
        Order buy_order3 = new Order(6, List.of(10, 9, 8, 7, 6, 5));

        blockchain.sendSecretInput(double_auction_contract, household1, createSecretInput(sell_order1), secretInputSellingRpc()); 
        blockchain.sendSecretInput(double_auction_contract, household2, createSecretInput(sell_order2), secretInputSellingRpc()); 
        blockchain.sendSecretInput(double_auction_contract, household3, createSecretInput(sell_order3), secretInputSellingRpc()); 
        blockchain.sendSecretInput(double_auction_contract, household4, createSecretInput(buy_order1), secretInputBuyingRpc());
        blockchain.sendSecretInput(double_auction_contract, household5, createSecretInput(buy_order2), secretInputBuyingRpc());
        blockchain.sendSecretInput(double_auction_contract, household6, createSecretInput(buy_order3), secretInputBuyingRpc());

        byte[] holdDoubleAuction = OptimizedZkDoubleAuction.holdDoubleAuction();

        // Act
        blockchain.sendAction(household1, double_auction_contract, holdDoubleAuction);

        // Assert
        OptimizedZkDoubleAuction.ContractState state = 
            OptimizedZkDoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.marketClearingPriceIndex()).isEqualTo((short)2);
        Assertions.assertThat(state.matchedOrders().size()).isEqualTo((short)3);
        Assertions.assertThat(state.matchedOrders().get(0)).isEqualTo(new OptimizedZkDoubleAuction.Trade((short)5, (short)1, (short)3));
        Assertions.assertThat(state.matchedOrders().get(1)).isEqualTo(new OptimizedZkDoubleAuction.Trade((short)6, (short)1, (short)2));
        Assertions.assertThat(state.matchedOrders().get(2)).isEqualTo(new OptimizedZkDoubleAuction.Trade((short)6, (short)3, (short)6));
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldNotAggregateOrders(){
        // Arrange
        Order sell_order1 = new Order(1, List.of(0, 0, 50, 0, 0, 0));
        Order sell_order2 = new Order(2, List.of(0, 0, 0, 0, 0, 0));

        Order buy_order1 = new Order(3, List.of(0, 0, 0, 100, 0, 0));
        Order buy_order2 = new Order(4, List.of(0, 0, 0, 0, 0, 0));

        // Act
        blockchain.sendSecretInput(double_auction_contract, household1, createSecretInput(sell_order1), secretInputSellingRpc()); 
        blockchain.sendSecretInput(double_auction_contract, household2, createSecretInput(sell_order2), secretInputSellingRpc()); 
        blockchain.sendSecretInput(double_auction_contract, household3, createSecretInput(buy_order1), secretInputBuyingRpc());
        blockchain.sendSecretInput(double_auction_contract, household4, createSecretInput(buy_order2), secretInputBuyingRpc());

        byte[] holdDoubleAuction = OptimizedZkDoubleAuction.holdDoubleAuction();

        blockchain.sendAction(household1, double_auction_contract, holdDoubleAuction);

        // Assert
        OptimizedZkDoubleAuction.ContractState state = 
            OptimizedZkDoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.marketClearingPriceIndex()).isEqualTo((short)4);
        Assertions.assertThat(state.matchedOrders().size()).isEqualTo((short)0);
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldHaveMarketClearingPriceIndexOnTheLeft(){
        // Arrange
        Order sell_order1 = new Order(1, List.of(0, 0, 5, 5, 10, 10));
        Order sell_order2 = new Order(2, List.of(0, 0, 0, 0, 0, 500));
        Order sell_order3 = new Order(3, List.of(5, 6, 7, 8, 9, 10));

        Order buy_order1 = new Order(4, List.of(5, 0, 0, 0, 0, 0));
        Order buy_order2 = new Order(5, List.of(0,0, 0, 0, 0, 0));
        Order buy_order3 = new Order(6, List.of(0, 0, 0, 0, 0, 0));

        blockchain.sendSecretInput(double_auction_contract, household1, createSecretInput(sell_order1), secretInputSellingRpc()); 
        blockchain.sendSecretInput(double_auction_contract, household2, createSecretInput(sell_order2), secretInputSellingRpc()); 
        blockchain.sendSecretInput(double_auction_contract, household3, createSecretInput(sell_order3), secretInputSellingRpc()); 
        blockchain.sendSecretInput(double_auction_contract, household4, createSecretInput(buy_order1), secretInputBuyingRpc());
        blockchain.sendSecretInput(double_auction_contract, household5, createSecretInput(buy_order2), secretInputBuyingRpc());
        blockchain.sendSecretInput(double_auction_contract, household6, createSecretInput(buy_order3), secretInputBuyingRpc());

        byte[] holdDoubleAuction = OptimizedZkDoubleAuction.holdDoubleAuction();

        // Act
        blockchain.sendAction(household1, double_auction_contract, holdDoubleAuction);

        // Assert
        OptimizedZkDoubleAuction.ContractState state = 
            OptimizedZkDoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.marketClearingPriceIndex()).isEqualTo((short)0);
        Assertions.assertThat(state.matchedOrders().size()).isEqualTo(1);
        Assertions.assertThat(state.matchedOrders().get(0)).isEqualTo(new OptimizedZkDoubleAuction.Trade((short)4, (short)3, (short)5));
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldReturnNoMatchedOrders(){
        // Arrange
        Order sell_order1 = new Order(1, List.of(0, 0, 5, 5, 10, 10));
        Order sell_order2 = new Order(2, List.of(0, 0, 0, 0, 0, 500));
        Order sell_order3 = new Order(3, List.of(5, 6, 7, 8, 9, 10));

        Order buy_order1 = new Order(4, List.of(5, 0, 0, 0, 0, 0));
        Order buy_order2 = new Order(5, List.of(15,0, 0, 0, 0, 0));
        Order buy_order3 = new Order(6, List.of(10, 0, 0, 0, 0, 0));

        blockchain.sendSecretInput(double_auction_contract, household1, createSecretInput(sell_order1), secretInputSellingRpc()); 
        blockchain.sendSecretInput(double_auction_contract, household2, createSecretInput(sell_order2), secretInputSellingRpc()); 
        blockchain.sendSecretInput(double_auction_contract, household3, createSecretInput(sell_order3), secretInputSellingRpc()); 
        blockchain.sendSecretInput(double_auction_contract, household4, createSecretInput(buy_order1), secretInputBuyingRpc());
        blockchain.sendSecretInput(double_auction_contract, household5, createSecretInput(buy_order2), secretInputBuyingRpc());
        blockchain.sendSecretInput(double_auction_contract, household6, createSecretInput(buy_order3), secretInputBuyingRpc());

        byte[] holdDoubleAuction = OptimizedZkDoubleAuction.holdDoubleAuction();

        // Act
        blockchain.sendAction(household1, double_auction_contract, holdDoubleAuction);

        // Assert
        OptimizedZkDoubleAuction.ContractState state = 
            OptimizedZkDoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.marketClearingPriceIndex()).isEqualTo((short)1);
        Assertions.assertThat(state.matchedOrders().size()).isEqualTo((short)0);
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldUpdatePrices()
    {
        // Arrange
        short min = 48; // Price in danish penny
        short max = 69; // Price in danish penny
        byte[] updatePrices = OptimizedZkDoubleAuction.updatePrices(min, max);

        // Act
        blockchain.sendAction(household1, double_auction_contract, updatePrices);

        // Assert
        OptimizedZkDoubleAuction.ContractState state = 
            OptimizedZkDoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

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
        byte[] updatePrices = OptimizedZkDoubleAuction.updatePrices((short)0, (short)0);

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
        byte[] reset = OptimizedZkDoubleAuction.resetContract();

        // Act
        blockchain.sendAction(household1, double_auction_contract, reset);

        // Assert
        OptimizedZkDoubleAuction.ContractState state = 
            OptimizedZkDoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.marketClearingPriceIndex()).isNull();
        Assertions.assertThat(state.matchedOrders()).isEmpty();
        Assertions.assertThat(state.prices().size()).isEqualTo(6);
        Assertions.assertThat(state.prices().get(0)).isEqualTo((short)0);
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldFailToResetContractBeforeAnAuctionIsHeld()
    {
        // Arrange
        byte[] reset = OptimizedZkDoubleAuction.resetContract();

        // Act & Assert
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
        byte[] setPrices = OptimizedZkDoubleAuction.updatePrices(min, max);

        // Act
        blockchain.sendAction(household1, double_auction_contract, setPrices);

        // Assert
        OptimizedZkDoubleAuction.ContractState state = 
            OptimizedZkDoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));
        
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
        Order sell_order1 = new Order(1, List.of(0, 2, 4, 6, 8, 10));
        Order sell_order2 = new Order(2, List.of(0, 0, 0, 0, 0, 50));

        Order buy_order1 = new Order(3, List.of(10, 8, 6, 4, 2, 0));
        Order buy_order2 = new Order(4, List.of(3,3, 3, 2, 0, 0));
        Order buy_order3 = new Order(5, List.of(0, 0, 0, 0, 0, 0));
        Order buy_order4 = new Order(6, List.of(0, 0, 0, 0, 0, 0));

        blockchain.sendSecretInput(double_auction_contract, household1, createSecretInput(sell_order1), secretInputSellingRpc()); 
        blockchain.sendSecretInput(double_auction_contract, household2, createSecretInput(sell_order2), secretInputSellingRpc());
        blockchain.sendSecretInput(double_auction_contract, household3, createSecretInput(buy_order1), secretInputBuyingRpc());
        blockchain.sendSecretInput(double_auction_contract, household4, createSecretInput(buy_order2), secretInputBuyingRpc());
        blockchain.sendSecretInput(double_auction_contract, household5, createSecretInput(buy_order3), secretInputBuyingRpc());
        blockchain.sendSecretInput(double_auction_contract, household6, createSecretInput(buy_order4), secretInputBuyingRpc());

        byte[] holdDoubleAuction = OptimizedZkDoubleAuction.holdDoubleAuction();

        // Act
        blockchain.sendAction(household1, double_auction_contract, holdDoubleAuction);

        // Assert
        OptimizedZkDoubleAuction.ContractState state = 
            OptimizedZkDoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));

        Assertions.assertThat(state.marketClearingPriceIndex()).isEqualTo((short)3);
        Assertions.assertThat(state.matchedOrders().size()).isEqualTo(2);
        Assertions.assertThat(state.matchedOrders().get(0)).isEqualTo(new Trade((short)3, (short)1, (short)4));
        Assertions.assertThat(state.matchedOrders().get(1)).isEqualTo(new Trade((short)4, (short)1, (short)2));
    }

    @ContractTest(previous = "shouldSetPrices")
    void shouldFailToHoldDoubleAuctionWhenNotContractOwner()
    {
        // Given
        byte[] holdDoubleAuction = OptimizedZkDoubleAuction.holdDoubleAuction();

        // When
        Assertions.assertThatThrownBy(
            () -> blockchain.sendAction(household2, double_auction_contract, holdDoubleAuction))
        .isInstanceOf(ActionFailureException.class)
        .hasMessageContaining("Only the auction holder can hold the auction!");
    }

    private record Order(int houseId, List<Integer> prices){ }

    private CompactBitArray createSecretInput(Order order) {
        return BitOutput.serializeBits(
            bitOutput -> {
            bitOutput.writeUnsignedInt(order.houseId, 16);
            bitOutput.writeUnsignedInt(order.prices.get(0), 16);
            bitOutput.writeUnsignedInt(order.prices.get(1), 16);
            bitOutput.writeUnsignedInt(order.prices.get(2), 16);
            bitOutput.writeUnsignedInt(order.prices.get(3), 16);
            bitOutput.writeUnsignedInt(order.prices.get(4), 16);
            bitOutput.writeUnsignedInt(order.prices.get(5), 16);
        });
    }

    byte[] secretInputBuyingRpc() {
        return new byte[] {0x40};
    }

    byte[] secretInputSellingRpc() {
        return new byte[] {0x45};
    }
}