public class PriceCalculator {
    private static final double TOLERANCE = 0.01;
    
    public boolean isPriceEqual(double price1, double price2) {
        return Math.abs(price1 - price2) < TOLERANCE;
    }
}