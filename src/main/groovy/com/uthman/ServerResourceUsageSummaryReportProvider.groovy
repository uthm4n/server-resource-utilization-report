package com.uthman

import com.morpheusdata.core.AbstractReportProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ReportResult
import com.morpheusdata.model.ReportResultRow
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.model.ContentSecurityPolicy
import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import io.reactivex.rxjava3.core.Observable
import groovy.util.logging.Slf4j
import java.util.Date

import java.sql.Connection

@Slf4j
class ServerResourceUsageSummaryReportProvider extends AbstractReportProvider{
	protected MorpheusContext morpheusContext
	protected Plugin plugin
	

	ServerResourceUsageSummaryReportProvider(Plugin plugin, MorpheusContext morpheusContext) {
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
		return "server-resource-usage-summary-report-report"
	}

	@Override
	String getName() {
		return "Server Resource Usage Summary"
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
		Connection dbConnection
		Long displayOrder = 0
		List<GroovyRowResult> results = []

		try {
			dbConnection = morpheus.async.report.getReadOnlyDatabaseConnection().blockingGet()
			if(reportResult.configMap?.timeIntervalFilter) {
				def timeInterval = reportResult.configMap?.timeIntervalFilter
				String dateTimeQuery = "DATE_SUB(NOW(), INTERVAL ${timeInterval} DAY)"

				results = new Sql(dbConnection).rows("SELECT account_id, id, name, used_cpu, used_memory, used_storage, last_stats FROM compute_server WHERE last_updated <= NOW() OR ${dateTimeQuery} LIMIT 10;")
			} 
		} finally {
			if(dbConnection){
				morpheus.async.report.releaseDatabaseConnection(dbConnection)
			}
		}
		
	       log.info("Results: ${results}")
	       Observable<GroovyRowResult> observable = Observable.fromIterable(results) as Observable<GroovyRowResult>
	       observable.map{ resultRow ->
	           log.info("Mapping resultRow ${resultRow}")
	           Map<String,Object> data = [ account_id: resultRow.account_id, id: resultRow.id, name: resultRow.name, used_cpu: resultRow.used_cpu, used_memory: resultRow.used_memory, used_storage: resultRow.used_storage, last_stats: resultRow.last_stats]
	           ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_MAIN, displayOrder: displayOrder++, dataMap: data)
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
		def intervalFilter = []
            intervalFilter << new OptionType (
                name: 'Time Interval',
                code: 'time-interval-filter',
                fieldName: 'timeIntervalFilter',
                displayOrder: 1,
                fieldLabel: 'Time Interval',
                helpText: 'Specify a number of days to look back',
                required: true,
                inputType: OptionType.InputType.NUMBER
            )

		return intervalFilter
	}


}
