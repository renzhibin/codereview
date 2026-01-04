public class OrderService {
    public List<OrderDTO> getOrders(List<Long> orderIds) {
        List<OrderDTO> result = new ArrayList<>();
        for (Long id : orderIds) {
            Order order = orderRepo.findById(id);
            if (order != null) {
                result.add(toDTO(order));
            }
        }
        return result;
    }
}