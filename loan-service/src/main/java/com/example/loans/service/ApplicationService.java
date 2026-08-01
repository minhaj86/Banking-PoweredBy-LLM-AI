package com.example.loans.service;

import com.example.loans.config.LoanProperties;
import com.example.loans.domain.entity.*;
import com.example.loans.domain.enums.ApplicationState;
import com.example.loans.domain.repository.*;
import com.example.loans.domain.valueobject.Decimals;
import com.example.loans.dto.request.*;
import com.example.loans.dto.response.*;
import com.example.loans.exception.LoanException;
import com.example.loans.mapper.Json;
import com.example.loans.security.Actor;
import com.example.loans.workflow.JobQueue;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class ApplicationService {
    private final ApplicationRepository applications;
    private final RevisionRepository revisions;
    private final DocumentRepository documents;
    private final AssessmentRepository assessments;
    private final DecisionRepository decisions;
    private final LoanRepository loans;
    private final ResourceAccess access;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final JobQueue jobs;
    private final Json json;
    private final LoanProperties config;
    private final Validator validator;

    @Transactional public ApplicationView create(Actor actor,String key,ApplicationInput input) {
        actor.require("APPLICANT"); validate(input);
        UUID id=idempotency.execute(actor,"CREATE_APPLICATION","applications",key,input,() -> {
            var app=ApplicationRecord.create(new ApplicationRecord.ActorData(actor.tenant(),actor.subject()),json.write(input));
            app.product(input.productId(),input.productVersion());applications.saveAndFlush(app);
            audit.record(actor,"APPLICATION_CREATED",app.getId(),0,app.getId()); return app.getId();
        });
        return view(access.application(actor,id,false));
    }
    @Transactional(readOnly=true) public ApplicationView get(Actor actor,UUID id) { return view(access.application(actor,id,false)); }
    @Transactional(readOnly=true) public Map<String,Object> list(Actor actor,int page,int size) {
        return queue(actor,page,size,null,null,null,null,"newest",null,"auto");
    }
    @Transactional public ApplicationView createDraft(Actor actor,String key,ApplicationInput input) {
        actor.require("APPLICANT"); validateDraft(input);
        UUID id=idempotency.execute(actor,"CREATE_DRAFT","applications",key,input,()->{
            var app=ApplicationRecord.create(new ApplicationRecord.ActorData(actor.tenant(),actor.subject()),json.write(input));
            app.product(input.productId(),input.productVersion()); applications.saveAndFlush(app);
            audit.record(actor,"APPLICATION_CREATED",app.getId(),0,app.getId());return app.getId();
        });return view(access.application(actor,id,false));
    }
    @Transactional public ApplicationView saveDraft(Actor actor,UUID id,long expected,String key,ApplicationInput input) {
        actor.require("APPLICANT"); validateDraft(input);
        idempotency.execute(actor,"SAVE_DRAFT",id.toString(),key,Map.of("version",expected,"input",input),()->{
            var app=access.application(actor,id,true);access.expected(app.getVersion(),expected);
            if(!app.getApplicant().equals(actor.subject())||!app.getState().editable()) throw LoanException.conflict("NOT_EDITABLE","Application cannot be edited in its current state");
            app.edit(json.write(input));app.product(input.productId(),input.productVersion());
            audit.record(actor,"APPLICATION_EDITED",id,app.getCurrentRevision(),id);return id;
        }); applications.flush();return view(access.application(actor,id,false));
    }
    private void validateDraft(ApplicationInput in) {
        if(in==null||json.write(in).length()>64000)throw LoanException.invalid("DRAFT_LIMIT","Draft is too large");
        var p=config.getProduct();
        if(!p.getId().equals(in.productId())||p.getVersion()!=in.productVersion())throw LoanException.invalid("PRODUCT_LIMITS","Select the current product version");
        if(in.requestedAmount()!=null&&!in.requestedAmount().isBlank())Decimals.money(in.requestedAmount());
    }
    @Transactional(readOnly=true) public Map<String,Object> queue(Actor actor,int page,int size,String status,String productId,java.time.LocalDate from,java.time.LocalDate to,String sort,String q,String scope) {
        actor.require("APPLICANT","LOAN_OFFICER","COMPLIANCE_REVIEWER","OPERATIONS");
        if(page<0||page>10000||size<1||size>100)throw LoanException.invalid("INVALID_PAGE","Invalid page bounds");
        boolean staff=scope.equals("staff")||(scope.equals("auto")&&actor.reviewer());
        if(!Set.of("mine","staff","auto").contains(scope))throw LoanException.invalid("INVALID_SCOPE","Unknown queue view");
        if(staff){actor.require("LOAN_OFFICER","COMPLIANCE_REVIEWER","OPERATIONS");}
        org.springframework.data.jpa.domain.Specification<ApplicationRecord> spec=(root,query,cb)->cb.equal(root.get("tenant"),actor.tenant());
        if(!staff)spec=spec.and((r,z,c)->c.equal(r.get("applicant"),actor.subject()));
        spec=spec.and((r,z,c)->c.isNull(r.get("purgedAt")));
        if(status!=null&&!status.isBlank()) { ApplicationState state;try{state=ApplicationState.valueOf(status);}catch(IllegalArgumentException e){throw LoanException.invalid("INVALID_STATUS","Unknown application state");} spec=spec.and((r,z,c)->c.equal(r.get("state"),state)); }
        if(productId!=null&&!productId.isBlank()){if(productId.length()>60)throw LoanException.invalid("INVALID_PRODUCT","Product filter too long");spec=spec.and((r,z,c)->c.equal(r.get("productId"),productId));}
        if(from!=null)spec=spec.and((r,z,c)->c.greaterThanOrEqualTo(r.get("createdAt"),from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
        if(to!=null)spec=spec.and((r,z,c)->c.lessThan(r.get("createdAt"),to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
        if(q!=null&&!q.isBlank()){UUID id;try{id=UUID.fromString(q);}catch(IllegalArgumentException e){throw LoanException.invalid("SEARCH_ID_REQUIRED","Search uses an exact application ID");}spec=spec.and((r,z,c)->c.equal(r.get("id"),id));}
        var order=switch(sort){case "oldest"->Sort.by("createdAt").ascending();case "updated"->Sort.by("updatedAt").descending();case "newest"->Sort.by("createdAt").descending();default->throw LoanException.invalid("INVALID_SORT","Unknown queue sort");};
        var result=applications.findAll(spec,PageRequest.of(page,size,order.and(Sort.by("id"))));
        var loanIds=new HashMap<UUID,UUID>();
        if(!result.isEmpty()) {var ids=result.getContent().stream().map(ApplicationRecord::getId).toList();loans.findByApplicationIdIn(ids).forEach(l->loanIds.put(l.getApplicationId(),l.getId()));}
        var items=result.getContent().stream().map(a->{Map<String,Object> row=new LinkedHashMap<>();row.put("id",a.getId());row.put("state",a.getState());row.put("version",a.getVersion());row.put("revision",a.getCurrentRevision());row.put("updatedAt",a.getUpdatedAt());row.put("productId",a.getProductId());row.put("loanId",loanIds.get(a.getId()));return row;}).toList();
        return Map.of("items",items,"page",page,"size",size,"total",result.getTotalElements());
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> timeline(Actor actor,UUID id) {
        access.application(actor,id,false);
        return jdbc.queryForList("SELECT action,revision,occurred_at FROM origination.audit_event WHERE tenant=? AND application_id=? AND action IN ('APPLICATION_CREATED','APPLICATION_SUBMITTED','HUMAN_REQUEST_INFORMATION','HUMAN_APPROVE','HUMAN_REJECT','LOAN_APPROVED','APPLICATION_WITHDRAWN','HUMAN_RECONSIDERATION_REQUESTED') ORDER BY occurred_at LIMIT 200",actor.tenant(),id);
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> informationRequests(Actor actor,UUID id) {
        access.application(actor,id,false);
        return decisions.findByApplicationIdOrderByCreatedAtAsc(id).stream().filter(d->d.getOutcome().equals("REQUEST_INFORMATION")).map(d->Map.<String,Object>of("id",d.getId(),"revision",d.getRevision(),"explanation",d.getApplicantExplanation(),"createdAt",d.getCreatedAt())).toList();
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> reviewRequests(Actor actor,UUID id) {
        access.application(actor,id,false);
        return jdbc.query("SELECT id,revision,payload,created_at FROM origination.review_request WHERE application_id=? ORDER BY created_at DESC LIMIT 100",(rs,n)->Map.<String,Object>of("id",rs.getObject(1),"revision",rs.getInt(2),"request",json.read(encryption.convertToEntityAttribute(rs.getString(3)),ReviewRequest.class),"createdAt",rs.getTimestamp(4).toInstant()),id);
    }
    @Transactional public ApplicationView edit(Actor actor,UUID id,long expected,ApplicationInput input) {
        actor.require("APPLICANT"); validate(input);
        var app=access.application(actor,id,true); access.expected(app.getVersion(),expected);
        if(!app.getApplicant().equals(actor.subject()) || !app.getState().editable()) throw LoanException.conflict("NOT_EDITABLE","Application cannot be edited in its current state");
        app.edit(json.write(input)); app.product(input.productId(),input.productVersion()); audit.record(actor,"APPLICATION_EDITED",id,app.getCurrentRevision(),id); applications.flush();
        return view(app);
    }
    @Transactional public ApplicationView submit(Actor actor,UUID id,long expected,String key) {
        actor.require("APPLICANT");
        Actor.requireVerifiedEmail();
        idempotency.execute(actor,"SUBMIT",id.toString(),key,Map.of("expectedVersion",expected),() -> {
            var app=access.application(actor,id,true); access.expected(app.getVersion(),expected);
            if(!app.getApplicant().equals(actor.subject()))throw LoanException.missing();
            if(!app.getState().editable()) throw LoanException.conflict("INVALID_TRANSITION","Application cannot be submitted in its current state");
            var input=json.read(app.getDraft(),ApplicationInput.class); validate(input);
            var all=documents.findByApplicationIdOrderByUploadedAtAsc(id);
            var superseded=new HashSet<>(all.stream().map(DocumentRecord::getSupersedes).filter(Objects::nonNull).toList());
            var current=all.stream().filter(d->!superseded.contains(d.getId()) && d.getPurgedAt()==null).toList();
            if(current.stream().anyMatch(d->!d.getUploadStatus().equals("COMPLETE"))) throw LoanException.conflict("INCOMPLETE_UPLOAD","Complete or replace pending uploads before submission");
            app.revise();
            var p=config.getProduct();
            revisions.saveAndFlush(ApplicationRevision.create(id,app.getCurrentRevision(),input.productId(),input.productVersion(),p.getPolicyVersion(),
                app.getDraft(),json.write(current.stream().map(DocumentRecord::getId).toList()),json.write(p)));
            jobs.enqueue("ASSESS",id,app.getCurrentRevision());
            audit.record(actor,"APPLICATION_SUBMITTED",id,app.getCurrentRevision(),id); return id;
        });
        applications.flush(); return view(access.application(actor,id,false));
    }
    @Transactional public ApplicationView withdraw(Actor actor,UUID id,long expected,String key) {
        actor.require("APPLICANT");
        idempotency.execute(actor,"WITHDRAW",id.toString(),key,Map.of("expectedVersion",expected),() -> {
            var app=access.application(actor,id,true); access.expected(app.getVersion(),expected);
            if(!app.getApplicant().equals(actor.subject()))throw LoanException.missing();
            app.transition(ApplicationState.WITHDRAWN); audit.record(actor,"APPLICATION_WITHDRAWN",id,app.getCurrentRevision(),id); return id;
        });
        applications.flush(); return view(access.application(actor,id,false));
    }
    @Transactional public UUID reviewRequest(Actor actor,UUID id,String key,ReviewRequest request) {
        actor.require("APPLICANT");
        var app=access.application(actor,id,true);
        if(!app.getApplicant().equals(actor.subject()))throw LoanException.missing();
        return idempotency.execute(actor,"RECONSIDERATION",id.toString(),key,request,() -> {
            UUID requestId=UUID.randomUUID();
            jdbcReview(requestId,app,actor,request);
            audit.record(actor,"HUMAN_RECONSIDERATION_REQUESTED",id,app.getCurrentRevision(),requestId); return requestId;
        });
    }
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.example.loans.security.SensitiveTextConverter encryption;
    private void jdbcReview(UUID id,ApplicationRecord app,Actor actor,ReviewRequest request) {
        jdbc.update("INSERT INTO origination.review_request(id,application_id,revision,requester,payload) VALUES(?,?,?,?,?)",id,app.getId(),app.getCurrentRevision(),actor.subject(),encryption.convertToDatabaseColumn(json.write(request)));
    }
    @Transactional(readOnly=true) public Map<String,Object> processing(Actor actor,UUID id) {
        var app=access.application(actor,id,false);
        if(actor.reviewer()) return Map.of("state",app.getState(),"revision",app.getCurrentRevision(),"version",app.getVersion(),"jobs",jobs.views(id));
        return Map.of("state",app.getState(),"revision",app.getCurrentRevision(),"version",app.getVersion());
    }
    @Transactional(readOnly=true) public AssessmentView assessment(Actor actor,UUID id) {
        actor.require("LOAN_OFFICER"); var app=access.application(actor,id,false);
        access.independentReviewer(actor,app);
        var a=assessments.findByApplicationIdAndRevision(id,app.getCurrentRevision()).orElseThrow(LoanException::missing);
        return json.read(a.getPayload(),AssessmentView.class);
    }
    public void validate(ApplicationInput in) {
        if(!validator.validate(in).isEmpty()) throw LoanException.invalid("APPLICATION_VALIDATION","Application fields failed validation");
        var p=config.getProduct(); var amount=Decimals.money(in.requestedAmount());
        if(!p.isEligible() || !in.productId().equals(p.getId()) || in.productVersion()!=p.getVersion() || amount.compareTo(p.getMinAmount())<0 || amount.compareTo(p.getMaxAmount())>0 || in.termMonths()<p.getMinMonths() || in.termMonths()>p.getMaxMonths()) throw LoanException.invalid("PRODUCT_LIMITS","Requested terms do not match an eligible product version");
        Decimals.optional(in.income().grossAmount()); Decimals.optional(in.income().netAmount());
        Decimals.optional(in.expenses().housing()); Decimals.optional(in.expenses().otherNonDebt());
        in.income().otherSources().forEach(i->Decimals.optional(i.netAmount()));
        in.debts().forEach(d->{Decimals.optional(d.outstandingBalance());Decimals.optional(d.creditLimit());Decimals.optional(d.utilisedBalance());Decimals.optional(d.monthlyRepayment());});
        if(!in.privacy().financialEvidenceProcessingAcknowledged()) throw LoanException.invalid("PRIVACY_NOTICE_REQUIRED","Applicable financial evidence notice must be acknowledged");
        if(in.employment().type().equals("EMPLOYED") && (in.employment().startDate()==null||in.employment().employer()==null||in.employment().employer().isBlank())) throw LoanException.invalid("EMPLOYMENT_FIELDS_REQUIRED","Employment verification fields are required");
    }
    private ApplicationView view(ApplicationRecord app) {
        var loan=loans.findByApplicationId(app.getId());
        var history=decisions.findByApplicationIdOrderByCreatedAtAsc(app.getId());
        return new ApplicationView(app.getId(),app.getVersion(),app.getState(),app.getCurrentRevision(),json.read(app.getDraft(),ApplicationInput.class),
            loan.map(LoanRecord::getId).orElse(null),history.isEmpty()?null:history.getLast().getApplicantExplanation(),app.getUpdatedAt());
    }
}

