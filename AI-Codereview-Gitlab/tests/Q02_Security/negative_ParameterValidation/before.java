package ai.src.main.test;

import java.util.regex.Pattern;

public class R159_ParameterValidationModule {
    
    public class ParameterValidationService {
        
        public boolean validateUsername(String username) {
            if (username == null || username.trim().isEmpty()) {
                return false;
            }
            
            if (username.length() < 3 || username.length() > 20) {
                return false;
            }
            
            Pattern pattern = Pattern.compile("^[a-zA-Z0-9_]+$");
            return pattern.matcher(username).matches();
        }
        
        public boolean validateEmail(String email) {
            if (email == null || email.trim().isEmpty()) {
                return false;
            }
            
            if (email.length() > 100) {
                return false;
            }
            
            Pattern pattern = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
            return pattern.matcher(email).matches();
        }
        
        public boolean validatePhoneNumber(String phoneNumber) {
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                return false;
            }
            
            if (phoneNumber.length() != 11) {
                return false;
            }
            
            Pattern pattern = Pattern.compile("^[0-9]+$");
            return pattern.matcher(phoneNumber).matches();
        }
        
        public boolean validatePassword(String password) {
            if (password == null || password.trim().isEmpty()) {
                return false;
            }
            
            if (password.length() < 8 || password.length() > 50) {
                return false;
            }
            
            boolean hasUpperCase = password.matches(".*[A-Z].*");
            boolean hasLowerCase = password.matches(".*[a-z].*");
            boolean hasDigit = password.matches(".*[0-9].*");
            boolean hasSpecialChar = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");
            
            return hasUpperCase && hasLowerCase && hasDigit && hasSpecialChar;
        }
        
        public boolean validateAge(int age) {
            return age >= 0 && age <= 150;
        }
        
        public boolean validateAmount(double amount) {
            return amount >= 0 && amount <= 1000000;
        }
    }
}