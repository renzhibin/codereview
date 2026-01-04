package ai.src.main.test;

import java.util.regex.Pattern;

public class R160_HTMLOutputFilterModule {
    
    public class HTMLOutputService {
        
        public String sanitizeUserInput(String userInput) {
            if (userInput == null) {
                return "";
            }
            
            return userInput
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
        }
        
        public String displayUserComment(String comment) {
            return sanitizeUserInput(comment);
        }
        
        public String displayUserName(String userName) {
            return sanitizeUserInput(userName);
        }
        
        public String displayUserDescription(String description) {
            return sanitizeUserInput(description);
        }
    }
}