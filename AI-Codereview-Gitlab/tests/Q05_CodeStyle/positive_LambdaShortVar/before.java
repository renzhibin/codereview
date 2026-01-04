public class MathUtils {
    public List<Integer> doubleValues(List<Integer> inputs) {
        return inputs.stream().map(input -> input * 2).collect(Collectors.toList());
    }
}