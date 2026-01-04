public class MathUtils {
    public List<Integer> doubleValues(List<Integer> inputs) {
        return inputs.stream()
            .map(x -> x * 2)
            .filter(n -> n > 0)
            .collect(Collectors.toList());
    }
}