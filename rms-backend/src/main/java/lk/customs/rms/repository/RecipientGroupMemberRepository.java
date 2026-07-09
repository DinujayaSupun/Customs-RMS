package lk.customs.rms.repository;

import lk.customs.rms.entity.RecipientGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecipientGroupMemberRepository extends JpaRepository<RecipientGroupMember, Long> {
    List<RecipientGroupMember> findByGroupId(Long groupId);

    // Bulk delete (not a derived deleteBy query): Hibernate flushes queued inserts before queued
    // entity removals, so replacing a group's members (delete-then-reinsert in the same
    // transaction) would otherwise hit the unique(group_id,user_id) constraint on re-save.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecipientGroupMember m where m.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Query("""
           select case when count(m) > 0 then true else false end
           from RecipientGroupMember m
           where m.groupId = :groupId and m.userId = :userId and m.isAdmin = true
           """)
    boolean isGroupAdmin(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Query("""
           select case when count(m) > 0 then true else false end
           from RecipientGroupMember m
           where m.groupId = :groupId and m.userId = :userId
           """)
    boolean isMember(@Param("groupId") Long groupId, @Param("userId") Long userId);

    // Batch form of isGroupAdmin, for mapping a page of documents without an N+1 query per row.
    @Query("""
           select m.groupId
           from RecipientGroupMember m
           where m.userId = :userId and m.isAdmin = true and m.groupId in :groupIds
           """)
    List<Long> findAdminGroupIds(@Param("userId") Long userId, @Param("groupIds") Collection<Long> groupIds);
}
