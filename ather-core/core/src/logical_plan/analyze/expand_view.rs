use crate::logical_plan::utils::belong_to_mdl;
use crate::mdl::utils::quoted;
use crate::mdl::{AnalyzedatherMDL, SessionStateRef};
use datafusion::common::tree_node::Transformed;
use datafusion::common::Result;
use datafusion::config::ConfigOptions;
use datafusion::logical_expr::{LogicalPlan, LogicalPlanBuilder};
use datafusion::optimizer::AnalyzerRule;
use std::fmt::Debug;
use std::sync::Arc;

pub struct ExpandatherViewRule {
    analyzed_ather_mdl: Arc<AnalyzedatherMDL>,
    session_state: SessionStateRef,
}

impl ExpandatherViewRule {
    pub fn new(
        analyzed_ather_mdl: Arc<AnalyzedatherMDL>,
        session_state: SessionStateRef,
    ) -> Self {
        Self {
            analyzed_ather_mdl,
            session_state,
        }
    }
}

impl Debug for ExpandatherViewRule {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ExpandatherViewRule").finish()
    }
}

impl AnalyzerRule for ExpandatherViewRule {
    fn analyze(&self, plan: LogicalPlan, _: &ConfigOptions) -> Result<LogicalPlan> {
        let plan = plan
            .transform_up_with_subqueries(|plan| match &plan {
                LogicalPlan::TableScan(table_scan) => {
                    if belong_to_mdl(
                        &self.analyzed_ather_mdl.ather_mdl(),
                        table_scan.table_name.clone(),
                        Arc::clone(&self.session_state),
                    ) && self
                        .analyzed_ather_mdl
                        .ather_mdl()
                        .get_view(table_scan.table_name.table())
                        .is_some()
                    {
                        if let Some(logical_plan) = table_scan.source.get_logical_plan() {
                            let subquery =
                                LogicalPlanBuilder::from(logical_plan.into_owned())
                                    .alias(quoted(table_scan.table_name.table()))?
                                    .build()?;
                            return Ok(Transformed::yes(subquery));
                        }
                    }
                    Ok(Transformed::no(plan))
                }
                _ => Ok(Transformed::no(plan)),
            })?
            .map_data(|plan| plan.recompute_schema())?
            .data;
        Ok(plan)
    }

    fn name(&self) -> &str {
        "ExpandatherViewRule"
    }
}
