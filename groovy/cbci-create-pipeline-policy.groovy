import com.cloudbees.pipeline.policy.Policy
import com.cloudbees.pipeline.rules.RulesInterceptionRule
import com.cloudbees.pipeline.rules.rules.EntirePipelineTimeoutRule
import com.cloudbees.pipeline.rules.RulesInterceptionManagement

import java.util.Arrays
import java.util.concurrent.TimeUnit

EntirePipelineTimeoutRule rule = new EntirePipelineTimeoutRule(30)
rule.setMaxUnit(TimeUnit.MINUTES)

Policy timeoutPolicy = new Policy("Timeout policy", Arrays.asList(rule), null, "fail", null, null)

RulesInterceptionManagement.get().setConfig(new RulesInterceptionManagement.Config(Arrays.asList(timeoutPolicy)))
