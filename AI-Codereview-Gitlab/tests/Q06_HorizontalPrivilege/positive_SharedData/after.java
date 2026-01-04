public class DocService {
    public Document getDoc(Long docId) {
        Document doc = repo.findById(docId);
        if (!doc.getOwnerId().equals(currentUser()) && !shareService.hasAccess(docId, currentUser())) {
            throw new ForbiddenException();
        }
        return doc;
    }
}