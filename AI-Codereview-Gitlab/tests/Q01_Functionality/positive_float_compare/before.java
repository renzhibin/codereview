public class PriceCalculator {
    public boolean isPriceEqual(BigDecimal price1, BigDecimal price2) {
        return price1.compareTo(price2) == 0;
    }
}