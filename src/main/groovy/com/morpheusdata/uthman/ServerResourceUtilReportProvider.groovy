package com.morpheusdata.uthman

import com.morpheusdata.core.AbstractReportProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.uthman.util.DateTimeUtils
import com.morpheusdata.model.ReportResult
import com.morpheusdata.model.ReportResultRow
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.uthman.util.ElasticsearchUtils
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.model.ContentSecurityPolicy
import io.reactivex.rxjava3.core.Observable
import groovy.util.logging.Slf4j

@Slf4j
class ServerResourceUtilReportProvider extends AbstractReportProvider{
	protected MorpheusContext morpheusContext
	protected Plugin plugin
	

	ServerResourceUtilReportProvider(Plugin plugin, MorpheusContext morpheusContext) {
		this.morpheusContext = morpheusContext
		this.plugin = plugin
	}
	
	@Override
	MorpheusContext getMorpheus() {
		return this.morpheusContext
	}

	@Override
	Plugin getPlugin() {
		return this.plugin
	}

	@Override
	String getCode() {
		return "server-resource-util-report"
	}

	@Override
	String getName() {
		return "Server Resource Utilization Report"
	}

	@Override
	ServiceResponse validateOptions(Map opts) {
		return ServiceResponse.success()
	}


	@Override
	HTMLResponse renderTemplate(ReportResult reportResult, Map<String, List<ReportResultRow>> reportRowsBySection) {
		ViewModel<String> model = new ViewModel<String>()
		def HashMap<String, String> reportPayload = new HashMap<String, String>();
		def webnonce = morpheus.services.getWebRequest().getNonceToken()
		reportPayload.put("webnonce",webnonce)
		reportPayload.put("reportdata",reportRowsBySection)
		model.object = reportPayload
		getRenderer().renderTemplate("hbs/serverResourceUsageSummaryReport", model)
	}

	@Override
	ContentSecurityPolicy getContentSecurityPolicy() {
		def csp = new ContentSecurityPolicy()
		csp.scriptSrc = 'https://cdn.jsdelivr.net'
		csp.frameSrc = '*.digitalocean.com'
		csp.imgSrc = '*.wikimedia.org'
		csp.styleSrc = 'https: *.bootstrapcdn.com'
		csp
	}

	@Override
	void process(ReportResult reportResult) {
		morpheus.async.report.updateReportResultStatus(reportResult,ReportResult.Status.generating).blockingAwait();
		Long displayOrder = 0
		List elasticApiResults = []
		def elasticQuery = ElasticsearchUtils.getQuery(true)

		try {
			String timeInterval = reportResult.configMap?.timePeriodFilter
			log.debug("timeInterval: ${timeInterval}")
			log.debug("reportResult configMap: ${reportResult.configMap}")
			if (timeInterval) {
				log.info("updating elasticsearch query with selected time filter...")
				try {
					elasticQuery.query.bool.filter.range.ts.gte = timeInterval
					log.debug("updated elasticsearch query: ${elasticQuery}")
				} catch (Exception e) {
					log.debug("error updating elasticsearch query: ${e}\r\n${elasticQuery}")
				}
				elasticApiResults << ElasticsearchUtils.executeQuery(elasticQuery).data
				log.debug("elasticApiResults: ${elasticApiResults}")
			}
		} catch (Exception e) {
			log.debug("error: ${e} // results: ${elasticApiResults}")
		}
		
		Observable<List> observable = Observable.fromIterable(elasticApiResults) as Observable<List>
	    observable.map{ resultRow ->
		
		log.info("Mapping resultRow ${resultRow}")
		Map<String,Object> data = [id: resultRow.id, used_storage: resultRow.used_storage, used_memory: resultRow.used_memory, used_cpu: resultRow.used_cpu]
		ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_MAIN, displayOrder: displayOrder++, dataMap: data)
			log.info("resultRowRecord: ${resultRowRecord.getProperties()}")
	           return resultRowRecord
	       }.buffer(50).doOnComplete {
	           morpheus.async.report.updateReportResultStatus(reportResult,ReportResult.Status.ready).blockingAwait();
	       }.doOnError { Throwable t ->
	           morpheus.async.report.updateReportResultStatus(reportResult,ReportResult.Status.failed).blockingAwait();
	       }.subscribe {resultRows ->
	           morpheus.async.report.appendResultRows(reportResult,resultRows).blockingGet()
	       }
	}

	@Override
	String getDescription() {
		return "A report that provides a summary on the used cpu, memory, and storage of compute servers"
	}

	@Override
	String getCategory() {
		return "inventory"
	}


	@Override
	Boolean getOwnerOnly() {
		return false
	}

	@Override
	Boolean getMasterOnly() {
		return false
	}

	@Override
	Boolean getSupportsAllZoneTypes() {
		return true
	}

	@Override
	List<OptionType> getOptionTypes() {
		[
				new OptionType (
					name: 'Time Period',
					code: 'time-period-filter',
					fieldContext: 'config',
					fieldName: 'timePeriodFilter',
					displayOrder: 1,
					fieldLabel: 'Time Period Filter',
					helpText: 'Select a time period to look at historical data',
					required: true,
					inputType: OptionType.InputType.SELECT,
					optionSource: 'serverResourceUtilReport.timePeriodSelector'
            	),
				new OptionType (
						name: 'Debugger',
						code: 'report-debugger',
						fieldContext: 'config.customOptions',
						fieldName: 'debugger',
						displayOrder: 2,
						fieldLabel: 'Debugger',
						dependsOn: 'timePeriodFilter',
						defaultValue: """ [ DEBUG ]
								
								getCurrentTime: ${DateTimeUtils.getCurrentDateTime(true)}

								getDateTimeRef: ${DateTimeUtils.getDateTimeRef("now")}
										90DAYS: ${DateTimeUtils.getDateTimeRef('now-90d/d')}
									   60 DAYS: ${DateTimeUtils.getDateTimeRef('now-60d/d')}
									
									morpheus context props: 
											morpheus props: ${this.morpheus.properties}
									 
						time interval: ${}
						report-provider props: ${ServerResourceUtilReportProvider.properties}
						
				
						""",
						helpText: 'Debugging',
						secretField: false,
						required: false,
						inputType: OptionType.InputType.CODE_EDITOR,
				)
		]
	}
}
