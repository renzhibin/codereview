public class OrderService {
    public void process(Order order) {
        if (order.getStatus() == 1) {
            ship(order);
        } else if (order.getStatus() == 2) {
            refund(order);
        } else if (order.getStatus() == 3) {
            archive(order);
        }
    }
}