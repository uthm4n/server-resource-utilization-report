package com.morpheusdata.uthman.datasets

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DatasetInfo
import com.morpheusdata.core.data.DatasetQuery
import com.morpheusdata.core.providers.AbstractDatasetProvider
import com.morpheusdata.uthman.util.ElasticsearchUtils
import io.reactivex.rxjava3.core.Observable
import groovy.util.logging.Slf4j

@Slf4j
class ElasticQueryDatasetProvider extends AbstractDatasetProvider<Map, String> {

	def elasticQuery = ElasticsearchUtils.getQuery(true)

	ElasticQueryDatasetProvider(Plugin plugin, MorpheusContext morpheus) {
		this.plugin = plugin
		this.morpheusContext = morpheus
	}

	@Override
	DatasetInfo getInfo() {
		new DatasetInfo(
			name: 'Elasticsearch query request and response data for the Server Resource Utilization Report',
			namespace: 'serverResourceUtilReport',
			key: 'elasticQuery',
			description: 'Better visibility over the elasticsearch query data used to populate the Server Resource Utilization Report'
		)
	}

	@Override
	String getName() {
		return 'elastic-query-view'
	}

	Class<Map> getItemType() {
		return Map.class
	}

	Observable<Map> list(DatasetQuery query) {
		return Observable.fromIterable(elasticQuery)
	}

	Observable<Map> listOptions(DatasetQuery query) {
		return Observable.fromIterable(elasticQuery)
	}

	Map fetchItem(Object value) {
		def rtn = null
		if(value instanceof String) {
			rtn = item((String)value)
		} else if(value instanceof CharSequence) {
			def stringValue = value.class == String ? (value as String) : null
			if(stringValue) {
				rtn = item(String as String)
			}
		}
		return rtn
	}

	Map item(String value) {
		def rtn = elasticQuery.find{ it.value == value }
		return rtn
	}

	String itemName(Map item) {
		return item.name
	}

	String itemValue(Map item) {
		return (String)item.value
	}

	@Override
	boolean isPlugin() {
		return true
	}
}