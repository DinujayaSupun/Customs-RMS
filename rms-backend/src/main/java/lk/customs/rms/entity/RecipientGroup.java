package lk.customs.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A named group of users that a document can be forwarded to. The group holds the document;
 * its admins (see {@link RecipientGroupMember#getIsAdmin()}) can act on it and its members are CC.
 * Referenced internally by {@code id} (stable, survives renames); the UI shows {@code name} + {@code color}.
 */
@Entity
@Table(name = "recipient_group", uniqueConstraints = @UniqueConstraint(name = "uk_recipient_group_name", columnNames = "name"))
@Getter
@Setter
public class RecipientGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipient_group_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "color", length = 20)
    private String color;

    // Optional avatar image (relative path under the upload dir); null → UI renders initials on color.
    @Column(name = "image_path", length = 255)
    private String imagePath;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
