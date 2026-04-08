package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MyWorkloadStatsResponse {

    private long assignedCount;
    private long openedCount;
    private long unopenedCount;
}
