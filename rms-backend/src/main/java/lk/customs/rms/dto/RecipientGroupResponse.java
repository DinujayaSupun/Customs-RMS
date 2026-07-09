package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RecipientGroupResponse {

    private Long id;
    private String name;
    private String color;
    private boolean hasImage;
    private Long createdByUserId;
    private String createdByName;
    private int adminCount;
    private int memberCount;
    private long documentsHeldCount;
    private List<Member> members;

    @Getter
    @Builder
    public static class Member {
        private Long userId;
        private String fullName;
        private String role;
        private Boolean isAdmin;
    }
}
