package com.morpheusdata.uthman.datasets

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DatasetInfo
import com.morpheusdata.core.data.DatasetQuery
import com.morpheusdata.core.providers.AbstractDatasetProvider
import com.morpheusdata.uthman.util.DateTimeUtils
import io.reactivex.rxjava3.core.Observable
import groovy.util.logging.Slf4j

@Slf4j
class DateTimeDatasetProvider extends AbstractDatasetProvider<Map, String> {

	def timeFilters = [
		[name:"Now", value:"now", dateTime:"${DateTimeUtils.getCurrentDateTime(true)}"],
		[name:"Last 30 days", value:"now-30d/d", dateTime:"${DateTimeUtils.getDateTimeRef("now-30d/d")}"],
		[name:"Last 60 days", value:"now-60d/d", dateTime:"${DateTimeUtils.getDateTimeRef("now-60d/d")}"],
        [name:"Last 90 days", value:"now-90d/d", dateTime:"${DateTimeUtils.getDateTimeRef("now-90d/d")}"]
    ]

	DateTimeDatasetProvider(Plugin plugin, MorpheusContext morpheus) {
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