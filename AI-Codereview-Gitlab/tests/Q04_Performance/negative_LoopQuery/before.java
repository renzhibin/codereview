public class OrderService {
    public List<OrderDTO> getOrders(List<Long> orderIds) {
        List<Order> orders = orderRepo.findByIds(orderIds);
        return convert(orders);
    }
}