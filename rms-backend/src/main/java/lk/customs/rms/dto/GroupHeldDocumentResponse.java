package lk.customs.rms.dto;

import lk.customs.rms.enums.Priority;
import lk.customs.rms.enums.Status;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** A document currently held by a group - just enough to list and link to it. */
@Getter
@Builder
public class GroupHeldDocumentResponse {
    private Long id;
    private String refNo;
    private String title;
    private Status status;
    private Priority priority;
    private LocalDateTime updatedAt;
}
