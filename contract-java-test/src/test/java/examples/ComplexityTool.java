package examples;

import com.partisiablockchain.BlockchainAddress;
import com.partisiablockchain.language.abicodegen.OptimizedZkDoubleAuction;
import com.partisiablockchain.language.junit.ContractBytes;
import com.partisiablockchain.language.junit.ContractTest;
import com.partisiablockchain.language.junit.JunitContractTest;
import com.partisiablockchain.language.junit.exceptions.ActionFailureException;
import com.secata.stream.CompactBitArray;
import com.secata.stream.BitOutput;
import org.assertj.core.api.Assertions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ComplexityTool extends JunitContractTest{

    private static final int BUYERS = 10;
    private static final int SELLERS = 10;
    private static final int LARGEST_ORDER = 100;

    private static final ContractBytes ZK_DOUBLE_AUCTION_BYTES = 
            ContractBytes.fromPaths(
            Path.of("../rust/target/wasm32-unknown-unknown/release/optimized_zk_double_auction.zkwa"),
            Path.of("../rust/target/wasm32-unknown-unknown/release/optimized_zk_double_auction.abi"),
            Path.of("../rust/target/wasm32-unknown-unknown/release/optimized_zk_double_auction_runner"));
    private List<BlockchainAddress> households;
    private BlockchainAddress double_auction_contract;

    @ContractTest
    void deploy() {
        households = new ArrayList<BlockchainAddress>();
        for (int i = 0; i < BUYERS + SELLERS; i++) {
            households.add(blockchain.newAccount(i+1));
        }

        byte[] initRpc = OptimizedZkDoubleAuction.initialize();

        double_auction_contract = blockchain.deployZkContract(households.get(0), ZK_DOUBLE_AUCTION_BYTES, initRpc);

        OptimizedZkDoubleAuction.ContractState state = 
            OptimizedZkDoubleAuction.ContractState.deserialize(blockchain.getContractState(double_auction_contract));
        
        short fit = 40; // Price in danish penny
        short priceAtT = 58; // Price in danish penny
        byte[] setPrices = OptimizedZkDoubleAuction.updatePrices(fit, priceAtT);
        blockchain.sendAction(households.get(0), double_auction_contract, setPrices);

        Assertions.assertThat(state).isNotNull();
        Assertions.assertThat(state.marketClearingPriceIndex()).isNull();
        Assertions.assertThat(state.matchedOrders()).isEmpty();
        Assertions.assertThat(state.prices().size()).isEqualTo(6);
        Assertions.assertThat(state.prices().get(0)).isEqualTo((short)0);
    }

    @ContractTest(previous = "deploy")
    void runDoubleAuction(){
        Random rand = new Random();

        for (int i = 0; i < SELLERS; i++){
            Order order = new Order(i, List.of(rand.nextInt(LARGEST_ORDER+1), rand.nextInt(LARGEST_ORDER+1), rand.nextInt(LARGEST_ORDER+1), rand.nextInt(LARGEST_ORDER+1), rand.nextInt(LARGEST_ORDER+1), rand.nextInt(LARGEST_ORDER+1)));
            blockchain.sendSecretInput(double_auction_contract, households.get(i), createSecretInput(order), secretInputSellingRpc()); 
        }
        for (int i = SELLERS; i < SELLERS+BUYERS; i++){
            Order order = new Order(i, List.of(rand.nextInt(LARGEST_ORDER+1), rand.nextInt(LARGEST_ORDER+1), rand.nextInt(LARGEST_ORDER+1), rand.nextInt(LARGEST_ORDER+1), rand.nextInt(LARGEST_ORDER+1), rand.nextInt(LARGEST_ORDER+1)));
            blockchain.sendSecretInput(double_auction_contract, households.get(i), createSecretInput(order), secretInputBuyingRpc()); 
        }

        byte[] holdDoubleAuction = OptimizedZkDoubleAuction.holdDoubleAuction();
        try {
            blockchain.sendAction(households.get(0), double_auction_contract, holdDoubleAuction);

        } catch(ActionFailureException e){
            throw e;
        }
        catch (RuntimeException e) {
            System.out.println("OBS error incountered: " + e.getClass().getName());
        }
        
        System.out.println("number of multiplications " + zkNodes.getComplexityOfLastComputation().multiplicationCount());
        System.out.println("number of rounds " + zkNodes.getComplexityOfLastComputation().numberOfRounds());
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
