/*
 * Copyright (C) 2019 The Turms Project
 * https://github.com/turms-im/turms
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.turms.service.domain.cluster.access.admin.controller;

import im.turms.server.common.access.admin.permission.RequiredPermission;
import im.turms.server.common.access.common.ResponseStatusCode;
import im.turms.server.common.domain.common.dto.response.ResponseDTO;
import im.turms.server.common.domain.common.dto.response.ResponseFactory;
import im.turms.server.common.infra.cluster.node.Node;
import im.turms.server.common.infra.cluster.node.NodeType;
import im.turms.server.common.infra.cluster.node.NodeVersion;
import im.turms.server.common.infra.cluster.service.config.entity.discovery.Leader;
import im.turms.server.common.infra.cluster.service.config.entity.discovery.Member;
import im.turms.server.common.infra.cluster.service.discovery.DiscoveryService;
import im.turms.server.common.infra.collection.CollectionUtil;
import im.turms.server.common.infra.exception.ResponseException;
import im.turms.server.common.infra.property.TurmsPropertiesManager;
import im.turms.service.domain.cluster.access.admin.dto.request.AddMemberDTO;
import im.turms.service.domain.cluster.access.admin.dto.request.UpdateMemberDTO;
import im.turms.service.domain.common.access.admin.controller.BaseController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

import static im.turms.server.common.access.admin.permission.AdminPermission.CLUSTER_LEADER_QUERY;
import static im.turms.server.common.access.admin.permission.AdminPermission.CLUSTER_LEADER_UPDATE;
import static im.turms.server.common.access.admin.permission.AdminPermission.CLUSTER_MEMBER_CREATE;
import static im.turms.server.common.access.admin.permission.AdminPermission.CLUSTER_MEMBER_DELETE;
import static im.turms.server.common.access.admin.permission.AdminPermission.CLUSTER_MEMBER_QUERY;
import static im.turms.server.common.access.admin.permission.AdminPermission.CLUSTER_MEMBER_UPDATE;


/**
 * @author James Chen
 */
@RestController
@RequestMapping("/cluster/members")
public class MemberController extends BaseController {

    private final DiscoveryService discoveryService;

    public MemberController(Node node, TurmsPropertiesManager propertiesManager) {
        super(propertiesManager);
        discoveryService = node.getDiscoveryService();
    }

    @GetMapping
    @RequiredPermission(CLUSTER_MEMBER_QUERY)
    public ResponseEntity<ResponseDTO<Collection<Member>>> queryMembers() {
        return ResponseFactory.okIfTruthy(discoveryService.getAllKnownMembers().values());
    }

    @DeleteMapping
    @RequiredPermission(CLUSTER_MEMBER_DELETE)
    public Mono<ResponseEntity<ResponseDTO<Void>>> removeMembers(@RequestParam List<String> ids) {
        Mono<Void> unregisterMembers = discoveryService.unregisterMembers(CollectionUtil.newSet(ids));
        return unregisterMembers.thenReturn(ResponseFactory.OK);
    }

    @PostMapping
    @RequiredPermission(CLUSTER_MEMBER_CREATE)
    public Mono<ResponseEntity<ResponseDTO<Void>>> addMember(@RequestBody AddMemberDTO addMemberDTO) {
        String clusterId = discoveryService.getLocalMember().getClusterId();
        NodeType nodeType = addMemberDTO.nodeType();
        if (nodeType != NodeType.SERVICE && addMemberDTO.isLeaderEligible()) {
            return Mono.error(ResponseException.get(ResponseStatusCode.ILLEGAL_ARGUMENT, "Only turms-service servers can be the leader"));
        }
        Member member = new Member(
                clusterId,
                addMemberDTO.nodeId(),
                addMemberDTO.zone(),
                nodeType,
                NodeVersion.parse(addMemberDTO.version()),
                addMemberDTO.isSeed(),
                addMemberDTO.isLeaderEligible(),
                addMemberDTO.registrationDate(),
                addMemberDTO.priority(),
                addMemberDTO.memberHost(),
                addMemberDTO.memberPort(),
                addMemberDTO.metricsApiAddress(),
                addMemberDTO.adminApiAddress(),
                addMemberDTO.wsAddress(),
                addMemberDTO.tcpAddress(),
                addMemberDTO.udpAddress(),
                false,
                addMemberDTO.isActive(),
                addMemberDTO.isHealthy());
        return discoveryService
                .registerMember(member)
                .thenReturn(ResponseFactory.OK);
    }

    @PutMapping
    @RequiredPermission(CLUSTER_MEMBER_UPDATE)
    public Mono<ResponseEntity<ResponseDTO<Void>>> updateMember(
            @RequestParam String id,
            @RequestBody UpdateMemberDTO updateMemberDTO) {
        Mono<Void> addMemberMono = discoveryService.updateMemberInfo(
                id,
                updateMemberDTO.zone(),
                updateMemberDTO.isSeed(),
                updateMemberDTO.isLeaderEligible(),
                updateMemberDTO.isActive(),
                updateMemberDTO.priority());
        return addMemberMono.thenReturn(ResponseFactory.OK);
    }

    // Leader

    @GetMapping("/leader")
    @RequiredPermission(CLUSTER_LEADER_QUERY)
    public ResponseEntity<ResponseDTO<Member>> queryLeader() {
        Leader leader = discoveryService.getLeader();
        if (leader == null) {
            throw ResponseException.get(ResponseStatusCode.NO_CONTENT);
        }
        String nodeId = leader.getNodeId();
        Member member = discoveryService.getAllKnownMembers().get(nodeId);
        return ResponseFactory.okIfTruthy(member);
    }

    @PostMapping("/leader")
    @RequiredPermission(CLUSTER_LEADER_UPDATE)
    public Mono<ResponseEntity<ResponseDTO<Member>>> electNewLeader(@RequestParam(required = false) String id) {
        Mono<Member> leader = id == null
                ? discoveryService.electNewLeaderByPriority()
                : discoveryService.electNewLeaderByNodeId(id);
        return ResponseFactory.okIfTruthy(leader);
    }

}