package lk.customs.rms.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ForwardReturnRequest {

    // Forward: exactly one of toUserId/toGroupId is required (checked in the service, since
    // bean validation can't express "either/or"). Return: both are ignored - the target is
    // derived from movement history instead.
    private Long toUserId;

    // Forward to a group: the group holds the document, its admins can act on it, its other
    // members become CC. Not used by return.
    private Long toGroupId;

    // Required for forward action: PRIVATE or PUBLIC
    private String forwardVisibility;

    // ✅ Optional remark to save before forward/return
    private String remarkText;

    @Size(max = 50, message = "CC recipients cannot exceed 50")
    private List<Long> ccUserIds;

    @Size(max = 50, message = "BCC recipients cannot exceed 50")
    private List<Long> bccUserIds;
}
