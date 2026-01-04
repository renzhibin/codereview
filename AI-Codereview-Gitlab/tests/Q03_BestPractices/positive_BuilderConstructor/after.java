public class UserDTO {
    private String name;
    private int age;
    private String address;
    private String phone;
    private String email;

    private UserDTO(String name, int age, String address, String phone, String email) {
        this.name = name;
        this.age = age;
        this.address = address;
        this.phone = phone;
        this.email = email;
    }
    
    public static class Builder { 
        // ... build() calls new UserDTO(...)
    }
}