package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

/** One DC -> receiver mapping, with names resolved for display. */
@Getter
@Builder
public class DcAutoForwardReceiverEntry {
    private Long dcUserId;
    private String dcName;
    private Long receiverUserId;
    private String receiverName;
    private String receiverRole;
}
