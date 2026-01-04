public class CustomerService {
    public void printBill(Customer customer) {
        double total = calculate(customer);
        System.out.println(total);
    }
}