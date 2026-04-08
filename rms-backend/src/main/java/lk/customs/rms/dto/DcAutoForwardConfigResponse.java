package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DcAutoForwardConfigResponse {
    private Boolean enabled;
    private Integer timeoutMinutes;
    private Long receiverUserId;
    private String receiverName;
    private String receiverRole;
}
