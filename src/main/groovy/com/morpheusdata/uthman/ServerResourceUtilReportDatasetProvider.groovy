package com.morpheusdata.uthman

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DatasetInfo
import com.morpheusdata.core.data.DatasetQuery
import com.morpheusdata.core.providers.AbstractDatasetProvider
import io.reactivex.rxjava3.core.Observable
import groovy.util.logging.Slf4j

@Slf4j
class ServerResourceUtilReportDatasetProvider extends AbstractDatasetProvider<Map, String> {

	static timeFilters = [
		[name:"Now", value:"NOW"],
		[name:"Last 30 days", value:"30DAYS"],
		[name:"Last 60 days", value:"60DAYS"],
        [name:"Last 90 days", value:"90DAYS"]
    ]

	ServerResourceUtilReportDatasetProvider(Plugin plugin, MorpheusContext morpheus) {
		this.plugin = plugin
		this.morpheusContext = morpheus
	}

	@Override
	DatasetInfo getInfo() {
		new DatasetInfo(
			name: 'Time period filter for the Server Resource Utilization Report',
			namespace: 'serverResourceUtilReport',
			key: 'timePeriodSelector',
			description: 'The select options for an optional time filter on the Server Resource Utilization Report'
		)
	}

	@Override
	String getName() {
		return 'date-time-period-filter'
	}

	Class<Map> getItemType() {
		return Map.class
	}

	Observable<Map> list(DatasetQuery query) {
		return Observable.fromIterable(timeFilters)
	}

	Observable<Map> listOptions(DatasetQuery query) {
		return Observable.fromIterable(timeFilters)
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
		def rtn = timeFilters.find{ it.value == value }
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