package com.morpheusdata.uthman

import com.morpheusdata.core.AbstractReportProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.uthman.util.DateTimeUtils
import com.morpheusdata.model.ReportResult
import com.morpheusdata.model.ReportResultRow
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.core.util.HttpApiClient.RequestOptions
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.model.ContentSecurityPolicy
import com.morpheusdata.core.util.MorpheusUtils
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
		def elasticApiResults = null
		Map elasticQuery = getElasticQuery()

		try {

			String timeInterval = reportResult.configMap?.timePeriodFilter
			if (timeInterval) {
				def currentDateTime = DateTimeUtils.getCurrentDateTime(true)
				def timeIntervalDateTime = DateTimeUtils.getDateTimeRef(timeInterval)
				updateElasticQuery(elasticQuery, "query.bool.filter.range.lastUpdated.lte", "${currentDateTime}")
				def updatedQuery = updateElasticQuery(elasticQuery, "query.bool.filter.range.lastUpdated.gte", "${timeIntervalDateTime}")
				log.info("Elastic Query: ${elasticQuery}")
				elasticApiResults = getElasticResponse(updatedQuery)
				log.debug("Elastic response: ${elasticApiResults}")
			}
		} catch (Exception e) {
			log.error("error getting data from elastic: ${e} // results: ${elasticApiResults}")
		}
		
	    log.info("Results: ${elasticApiResults}")
		Observable<List> observable = Observable.fromIterable(elasticApiResults) as Observable<List>
	    observable.map{ resultRow ->
		
		log.info("Mapping resultRow ${resultRow}")
		Map<String,Object> data = [id: resultRow.id, account_id: resultRow.account_id, name: resultRow.name, used_storage: resultRow.usedStorage, used_cpu: resultRow.usedCpu, used_memory: resultRow.usedMemory] 
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
					fieldContext: 'config.customOptions',
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

								getDateTimeRef: ${DateTimeUtils.getDateTimeRef("60DAYSAGO")}
						getDateTimeRef(90DAYS): ${DateTimeUtils.getDateTimeRef('90DAYSAGO')}
									
										Plugin: ${this.getPlugin()}
							   Plugin settings: ${this.getPlugin().settings}
							   		Properties: ${this.getPlugin().properties}	
				
									morpheus context props: ${this.morpheusContext.properties}
											morpheus props: ${this.morpheus.properties}
											morpheus props: ${ServerResourceUtilReportProvider.properties}
				
						""",
						helpText: 'Debugging',
						required: false,
						inputType: OptionType.InputType.CODE_EDITOR,
				)
		]
	}

	public static Map getElasticQuery() {
		def query = """{"query":{"bool":{"must":[],"filter":[{"bool":{"filter":[{"bool":{"should":[{"exists":{"field":"serverType"}}],"minimum_should_match":1}},{"bool":{"should":[{"exists":{"field":"usedCpu"}}],"minimum_should_match":1}},{"bool":{"should":[{"exists":{"field":"usedMemory"}}],"minimum_should_match":1}},{"bool":{"should":[{"exists":{"field":"usedStorage"}}],"minimum_should_match":1}}]}},{"range":{"lastUpdated":{"format":"strict_date_optional_time","gte":"2024-04-23","lte":"2024-05-22T23:00:00"}}}],"should":[],"must_not":[]}},"_source":["_id","name","account","usedCpu","usedMemory","usedStorage"]}"""
		try {
			query = MorpheusUtils.getJson(query)
		} catch (Exception e) {
			log.debug([success: false, error: "Failed to convert Elasticsearch query into Map: ${query}, exception: ${e}"] as String)
		}
		return query as Map
	}

	public static Map updateElasticQuery(Map elasticQuery, String path, Object updatedValue) {
		if (elasticQuery && path && updatedValue) {
			def keys = path.tokenize('.')
			def current = elasticQuery
			for (int i = 0; i < keys.size() - 1; i++) { current = current[keys[i]] }
    		current[keys.last()] = updatedValue
    	
		}
		return elasticQuery 
	}

    static def getElasticResponse(Map requestBody) {
		def client = new HttpApiClient()
		def elasticUrl = "http://10.32.23.71:9200"
		def searchPath = "/_search"

        RequestOptions requestOptions = new RequestOptions()
		requestOptions.body = requestBody

		def elasticResponse = client.callJsonApi(elasticUrl, searchPath, requestOptions, 'POST')
		def elasticResults = []
		if (elasticResponse.success) {
			def hits = elasticResponse.data.hits.hits
			try {
				hits.each { hit ->
				elasticResults << ["id": hit._id, "account_id": hit.account.id, "name": hit._source.name, "used_storage": hit._source.usedStorage, "used_cpu": hit._source.usedCpu, "used_memory": hit._source.usedMemory] 
				}
			} catch (Exception e) {
				log.debug("Failed to construct data from elastic response: ${e}")
			}
		} else {
			log.error("Error getting data from elasticsearch: ${elasticResponse.error ?: elasticResponse.data.errors}")
		}
		return elasticResults
	}

}
