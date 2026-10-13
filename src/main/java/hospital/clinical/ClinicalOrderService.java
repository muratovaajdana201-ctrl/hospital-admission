package hospital.clinical;

import java.util.List;
import java.util.Optional;


public class ClinicalOrderService {
    private final ClinicalOrderRepository repository;
    private final DrugSafetyChecker checker;

    public ClinicalOrderService(ClinicalOrderRepository repository, DrugSafetyChecker checker) {
        this.repository = repository;
        this.checker = checker;
    }


    public ClinicalOrder place(String chainId, String labTest, String medication,
                               List<String> allergies) {
        checker.validate(labTest, medication);
        checker.checkSafe(medication, allergies);

        ClinicalOrder order = new ClinicalOrder(chainId, labTest, medication);
        order.moveTo(OrderStatus.CONFIRMED);
        repository.place(order);   // транзакция: списание лекарства + запись заказа
        return order;
    }


    public void cancelByChain(String chainId) {
        repository.findByChainId(chainId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.CONFIRMED) {
                order.moveTo(OrderStatus.CANCELLED);
                repository.cancel(order);
            }
        });
    }

    public Optional<ClinicalOrder> findByChain(String chainId) {
        return repository.findByChainId(chainId);
    }

    public List<ClinicalOrder> findAll() {
        return repository.findAll();
    }



    public void setStock(String medication, int available) {
        repository.setStock(medication, available);
    }

    public int stockOf(String medication) {
        return repository.stockOf(medication);
    }
}