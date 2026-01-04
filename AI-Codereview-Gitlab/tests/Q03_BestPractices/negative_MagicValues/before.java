public class OrderService {
    public void process(Order order) {
        if (order.getStatus() == OrderStatus.PAID) {
            ship(order);
        } else if (order.getStatus() == OrderStatus.CANCELLED) {
            refund(order);
        }
    }
}