package lk.customs.rms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SaveRecipientGroupRequest {

    @NotBlank
    private String name;

    private String color;

    @Valid
    @Size(min = 1, message = "A group needs at least one member")
    private List<Member> members;

    @Getter
    @Setter
    public static class Member {
        @NotNull
        private Long userId;

        @NotNull
        private Boolean isAdmin;
    }
}
