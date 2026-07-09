package lk.customs.rms.service.impl;

import lk.customs.rms.dto.GroupHeldDocumentResponse;
import lk.customs.rms.dto.RecipientGroupResponse;
import lk.customs.rms.dto.SaveRecipientGroupRequest;
import lk.customs.rms.entity.RecipientGroup;
import lk.customs.rms.entity.RecipientGroupMember;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.enums.Status;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.exception.ResourceNotFoundException;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.RecipientGroupMemberRepository;
import lk.customs.rms.repository.RecipientGroupRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.PermissionService;
import lk.customs.rms.service.RecipientGroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RecipientGroupServiceImpl implements RecipientGroupService {

    private final RecipientGroupRepository groupRepository;
    private final RecipientGroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    public RecipientGroupServiceImpl(RecipientGroupRepository groupRepository,
                                     RecipientGroupMemberRepository memberRepository,
                                     UserRepository userRepository,
                                     DocumentRepository documentRepository,
                                     PermissionService permissionService,
                                     AuditLogService auditLogService) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipientGroupResponse> list() {
        return groupRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupHeldDocumentResponse> documentsHeldBy(Long groupId) {
        requireGroup(groupId);
        return documentRepository.findByCurrentOwnerGroupIdAndDeletedFalseOrderByUpdatedAtDesc(groupId).stream()
                .map(d -> GroupHeldDocumentResponse.builder()
                        .id(d.getId())
                        .refNo(d.getRefNo())
                        .title(d.getTitle())
                        .status(d.getStatus())
                        .priority(d.getPriority())
                        .updatedAt(d.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public RecipientGroupResponse create(SaveRecipientGroupRequest request, User actor) {
        permissionService.ensurePermission(actor.getId(), AppPermission.MANAGE_GROUPS,
                "You do not have permission to create groups.");

        String name = requireName(request);
        if (groupRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("A group named '" + name + "' already exists.");
        }

        Map<Long, Boolean> memberAdminFlags = normalizeMembers(request);
        // The creator is always an admin of a group they create, even if they left themselves
        // out of the member list or marked themselves a plain member.
        memberAdminFlags.put(actor.getId(), true);
        validateMembersExist(memberAdminFlags.keySet());

        RecipientGroup group = new RecipientGroup();
        group.setName(name);
        group.setColor(request.getColor());
        group.setCreatedByUserId(actor.getId());
        group.setCreatedAt(LocalDateTime.now());
        group = groupRepository.save(group);

        saveMembers(group.getId(), memberAdminFlags);

        auditLogService.logEventWithDetails("RECIPIENT_GROUP", group.getId(), "GROUP_CREATE", actor.getId(),
                "Created group '" + name + "'", Map.of("memberCount", memberAdminFlags.size()));

        return toResponse(group);
    }

    @Override
    @Transactional
    public RecipientGroupResponse update(Long groupId, SaveRecipientGroupRequest request, User actor) {
        RecipientGroup group = requireGroup(groupId);
        ensureCanManage(group, actor);

        String name = requireName(request);
        if (!name.equalsIgnoreCase(group.getName()) && groupRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("A group named '" + name + "' already exists.");
        }

        Map<Long, Boolean> memberAdminFlags = normalizeMembers(request);
        validateMembersExist(memberAdminFlags.keySet());
        if (memberAdminFlags.values().stream().noneMatch(Boolean::booleanValue)) {
            throw new BadRequestException("A group needs at least one admin.");
        }

        group.setName(name);
        group.setColor(request.getColor());
        group.setUpdatedAt(LocalDateTime.now());
        groupRepository.save(group);

        memberRepository.deleteByGroupId(groupId);
        saveMembers(groupId, memberAdminFlags);

        auditLogService.logEventWithDetails("RECIPIENT_GROUP", group.getId(), "GROUP_UPDATE", actor.getId(),
                "Updated group '" + name + "'", Map.of("memberCount", memberAdminFlags.size()));

        return toResponse(group);
    }

    @Override
    @Transactional
    public void delete(Long groupId, User actor) {
        RecipientGroup group = requireGroup(groupId);
        ensureCanManage(group, actor);

        long heldDocs = documentRepository.countByCurrentOwnerGroupIdAndDeletedFalseAndStatusNot(groupId, Status.ISSUED);
        if (heldDocs > 0) {
            throw new BadRequestException(
                    "This group is holding " + heldDocs + " active document(s); reassign them before deleting.");
        }

        memberRepository.deleteByGroupId(groupId);
        groupRepository.delete(group);

        auditLogService.logEventWithDetails("RECIPIENT_GROUP", groupId, "GROUP_DELETE", actor.getId(),
                "Deleted group '" + group.getName() + "'", Map.of());
    }

    // ---- helpers ----

    private void ensureCanManage(RecipientGroup group, User actor) {
        boolean systemAdmin = actor.getRole() != null && "ADMIN".equalsIgnoreCase(actor.getRole().getRoleName());
        if (systemAdmin) {
            return;
        }
        if (!memberRepository.isGroupAdmin(group.getId(), actor.getId())) {
            throw new BadRequestException("Only an admin of this group can manage it.");
        }
    }

    private String requireName(SaveRecipientGroupRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Group name is required.");
        }
        return name;
    }

    private Map<Long, Boolean> normalizeMembers(SaveRecipientGroupRequest request) {
        Map<Long, Boolean> flags = new LinkedHashMap<>();
        if (request.getMembers() != null) {
            for (SaveRecipientGroupRequest.Member member : request.getMembers()) {
                flags.merge(member.getUserId(), Boolean.TRUE.equals(member.getIsAdmin()), (a, b) -> a || b);
            }
        }
        if (flags.isEmpty()) {
            throw new BadRequestException("A group needs at least one member.");
        }
        return flags;
    }

    private void validateMembersExist(Set<Long> userIds) {
        List<User> found = userRepository.findAllById(userIds);
        if (found.size() != userIds.size()) {
            throw new BadRequestException("One or more selected members do not exist.");
        }
        for (User user : found) {
            if (!Boolean.TRUE.equals(user.getIsActive())) {
                throw new BadRequestException("User '" + user.getFullName() + "' is not active.");
            }
        }
    }

    private void saveMembers(Long groupId, Map<Long, Boolean> memberAdminFlags) {
        memberAdminFlags.forEach((userId, isAdmin) -> {
            RecipientGroupMember member = new RecipientGroupMember();
            member.setGroupId(groupId);
            member.setUserId(userId);
            member.setIsAdmin(isAdmin);
            memberRepository.save(member);
        });
    }

    private RecipientGroup requireGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
    }

    private RecipientGroupResponse toResponse(RecipientGroup group) {
        List<RecipientGroupMember> members = memberRepository.findByGroupId(group.getId());
        Map<Long, User> usersById = new LinkedHashMap<>();
        if (!members.isEmpty()) {
            userRepository.findAllById(members.stream().map(RecipientGroupMember::getUserId).toList())
                    .forEach(u -> usersById.put(u.getId(), u));
        }

        List<RecipientGroupResponse.Member> memberDtos = members.stream()
                .map(m -> {
                    User user = usersById.get(m.getUserId());
                    return RecipientGroupResponse.Member.builder()
                            .userId(m.getUserId())
                            .fullName(user == null ? "Unknown user" : user.getFullName())
                            .role(user == null || user.getRole() == null ? null : user.getRole().getRoleName())
                            .isAdmin(Boolean.TRUE.equals(m.getIsAdmin()))
                            .build();
                })
                .toList();

        int adminCount = (int) memberDtos.stream().filter(m -> Boolean.TRUE.equals(m.getIsAdmin())).count();
        User creator = userRepository.findById(group.getCreatedByUserId()).orElse(null);
        long documentsHeldCount = documentRepository
                .countByCurrentOwnerGroupIdAndDeletedFalseAndStatusNot(group.getId(), Status.ISSUED);

        return RecipientGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .color(group.getColor())
                .hasImage(group.getImagePath() != null)
                .createdByUserId(group.getCreatedByUserId())
                .createdByName(creator == null ? null : creator.getFullName())
                .adminCount(adminCount)
                .memberCount(memberDtos.size())
                .documentsHeldCount(documentsHeldCount)
                .members(memberDtos)
                .build();
    }
}
