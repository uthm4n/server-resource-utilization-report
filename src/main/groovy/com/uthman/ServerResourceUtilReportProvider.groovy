package com.morpheusdata.uthman

import com.morpheusdata.core.AbstractReportProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.uthman.ServerResourceUtilReportDatasetProvider
import com.morpheusdata.model.ReportResult
import com.morpheusdata.model.ReportResultRow
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.core.util.HttpApiClient.RequestOptions
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.core.util.MorpheusUtils
import com.morpheusdata.model.ContentSecurityPolicy
import groovy.sql.GroovyRowResult
import com.morpheusdata.core.util.MorpheusUtils
import java.time.LocalDateTime 
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import groovy.sql.Sql
import io.reactivex.rxjava3.core.Observable
import groovy.util.logging.Slf4j
import java.util.Date

import java.sql.Connection

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
		Connection dbConnection
		Long displayOrder = 0
		List<GroovyRowResult> results = []
		Map elasticQuery = getElasticSearchQuery()
		List serverResourceData = []

		try {

			def timeInterval = reportResult.configMap?.timePeriodFilter
			if (timeInterval) {
				elasticQuery.query.bool.filter.range.lastUpdated.lte = getCurrentTime()
				elasticQuery.query.bool.filter.range.lastUpdated.gte = getDateTimeRef(timeInterval)
				serverResourceData << getDataFromElastic(elasticQuery)
				log.info("Elastic Query: ${elasticQuery}")
			}
				results = getDataFromElastic(elasticQuery)
		} catch (Exception e) {
			log.error("error getting data from elastic: ${e} // results: ${results}")
		}
		
	    log.info("Results: ${results}")
		Observable<GroovyRowResult> observable = Observable.fromIterable(results) as Observable<GroovyRowResult>
	    observable.map{ resultRow ->
		
		log.info("Mapping resultRow ${resultRow}")
		Map<Object,Object> data = [account_id: resultRow._source.account.id, id:resultRow._id, name: resultRow._source.name, usedStorage: resultRow._source.used_storage, usedCpu: resultRow._source.used_cpu, usedMemory: resultRow._source.used_memory] 
		ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_MAIN, displayOrder: displayOrder++, dataMap: data)
			log.info("resultRowRecord: ${resultRowRecord}")
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
		[ new OptionType (
                name: 'Time Period',
                code: 'time-period-filter',
				fieldContext: 'config.customOptions',
                fieldName: 'timePeriodFilter',
                displayOrder: 1,
                fieldLabel: 'Time Period Filter',
                helpText: 'select a time filter to look back: now, 30-days, 60-days, 90-days',
                required: true,
                inputType: OptionType.InputType.SELECT,
				optionSource: 'serverResourceUtilReport.timePeriodSelector'
            )
		]
	}

	public static String getCurrentTime() {
		def now = LocalDateTime.now()
		def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
		def currentDateTime = now.format(formatter) 

		return currentDateTime
	}

	public static String getDateTimeRef(String timeInterval) {
		def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss") 
		switch(timeInterval) {
			case "90DAYS":
				def now = getCurrentTime()
				def ninetyDayInterval = now.minus(90, ChronoUnit.DAYS)
				def dateTimeRef = ninetyDayInterval.format(formatter)
			return dateTimeRef
				break
			
			case "60DAYS":
				def now = getCurrentTime()
				def sixtyDayInterval = now.minus(60, ChronoUnit.DAYS)
				def dateTimeRef = sixtyDayInterval.format(formatter)
			return dateTimeRef
				break
			
			case "30DAYS":
				def now = getCurrentTime()
				def thirtyDayInterval = now.minus(30, ChronoUnit.DAYS)
				def dateTimeRef = thirtyDayInterval.format(formatter)
			return dateTimeRef
				break
		}
	}

	public static Map getElasticSearchQuery() {
		def query = """{"query":{"bool":{"must":[],"filter":[{"bool":{"filter":[{"bool":{"should":[{"exists":{"field":"serverType"}}],"minimum_should_match":1}},{"bool":{"should":[{"exists":{"field":"usedCpu"}}],"minimum_should_match":1}},{"bool":{"should":[{"exists":{"field":"usedMemory"}}],"minimum_should_match":1}},{"bool":{"should":[{"exists":{"field":"usedStorage"}}],"minimum_should_match":1}}]}},{"range":{"lastUpdated":{"format":"strict_date_optional_time","gte":"2024-04-23","lte":"2024-05-22T23:00:00"}}}],"should":[],"must_not":[]}},"_source":["_id","name","account","usedCpu","usedMemory","usedStorage"]}"""
		try {
			query = MorpheusUtils.getJson(query)
		} catch (Exception e) {
			log.error("Failed to convert Elasticsearch Query: ${query}")
		}
		return query
	}

	def getDataFromElastic(Map requestBody) {
		def client = new HttpApiClient()
		def elasticUrl = "http://10.32.23.71:9200"
		def searchPath = "/_search"
		
		HttpApiClient.RequestOptions requestOptions = new HttpApiClient.RequestOptions()
		requestOptions.body = requestBody

		def elasticResponse = client.callJsonApi(elasticUrl, searchPath, requestOptions, 'POST')
		if (elasticResponse.success) {
			return elasticResponse.data?.hits?.hits
		}
	}

}
