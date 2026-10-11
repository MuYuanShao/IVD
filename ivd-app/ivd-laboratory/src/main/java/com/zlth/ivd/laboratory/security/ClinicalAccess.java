package com.zlth.ivd.laboratory.security;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.common.exception.BizException;
import java.util.Set;
public final class ClinicalAccess {
 private ClinicalAccess(){}
 public static InstitutionContext.Scope requireClinical(){
  var scope=InstitutionContext.require();
  if((!Set.of("LAB_TECH","LAB_DOCTOR","LAB_DIRECTOR").contains(scope.role()) && !com.zlth.ivd.common.utils.SecurityUtils.isFullAccessAdmin()) || scope.actorId()==null || scope.departmentId()==null)
   throw new BizException(40300,"该操作需要具备科室归属的临床用户");
  return scope;
 }
 public static void requireMasterWriter(){
  if(!Set.of("SYSTEM_ADMIN","LAB_DIRECTOR").contains(InstitutionContext.require().role())) throw new BizException(40300,"没有基础资料维护权限");
  InstitutionContext.requireUserActor();
  if(InstitutionContext.require().role().equals("LAB_DIRECTOR")) requireClinical();
 }
 public static void requireSigner(){if(!requireClinical().role().equals("LAB_DIRECTOR") && !com.zlth.ivd.common.utils.SecurityUtils.isFullAccessAdmin())throw new BizException(40300,"仅主任可签发报告");}
}
