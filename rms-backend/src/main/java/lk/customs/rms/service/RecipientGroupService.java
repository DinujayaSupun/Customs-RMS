package lk.customs.rms.service;

import lk.customs.rms.dto.GroupHeldDocumentResponse;
import lk.customs.rms.dto.RecipientGroupResponse;
import lk.customs.rms.dto.SaveRecipientGroupRequest;
import lk.customs.rms.entity.User;

import java.util.List;

public interface RecipientGroupService {
    List<RecipientGroupResponse> list();
    RecipientGroupResponse create(SaveRecipientGroupRequest request, User actor);
    RecipientGroupResponse update(Long groupId, SaveRecipientGroupRequest request, User actor);
    void delete(Long groupId, User actor);
    List<GroupHeldDocumentResponse> documentsHeldBy(Long groupId);
}
