package ai.src.main.test;

public class R160_HTMLOutputFilterModule {
    
    public class HTMLOutputService {
        
        public String displayUserInput(String userInput) {
            return userInput;
        }
        
        public String displayUserComment(String comment) {
            return comment;
        }
        
        public String displayUserName(String userName) {
            return userName;
        }
        
        public String displayUserDescription(String description) {
            return description;
        }
        
        public String generateHTML(String userContent) {
            return "<div>" + userContent + "</div>";
        }
    }
}