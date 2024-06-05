package com.morpheusdata.uthman.util

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.core.util.MorpheusUtils
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.uthman.ServerResourceUtilReportPlugin
import groovy.json.JsonSlurper
import groovy.*
import groovy.util.logging.Slf4j
import org.apache.groovy.json.internal.LazyMap

@Slf4j
class ElasticsearchUtils {
    MorpheusContext morpheusContext
    Plugin plugin
    ServerResourceUtilReportPlugin serverResourceUtilReportPlugin


    ElasticsearchUtils(Plugin plugin, MorpheusContext morpheusContext) {
        this.morpheusContext = morpheusContext
        this.plugin = plugin
    }

    public static String getApplianceUrl(Plugin plugin) {
        def settings = plugin.settings
        def applianceUrl = ""

        try {
            def options = MorpheusUtils.getJson(settings)
            applianceUrl = options.get('applianceUrl')
        } catch (Exception e) {
            log.error("error getting appliance url... please ensure that this is set in the plugin settings. Error: ${e}")
        }
        return applianceUrl
    }

    public Map getConnectionConfig() {
        HttpApiClient elasticClient = new HttpApiClient()
        def applianceUrl = getApplianceUrl(plugin)
        def config = [elasticsearch: [host: "", port:0, url: ""]]

        def morpheusApiHealth = elasticClient.callJsonApi(applianceUrl, '/api/health', null, "GET")
        if (morpheusApiHealth.success) {
            try {
                config.elasticsearch.host = morpheusApiHealth.data.health.elastic.master.ip
                config.elasticsearch.port = 9200
                config.elasticsearch.url = "${config.elasticsearch.host}:${config.elasticsearch.port}"
            } catch (Exception e) {
                log.debug("error getting elasticsearch connection details: ${e}")
            } finally {
                elasticClient.shutdownClient()
            }
        }
        return config
    }

     static testConnection() {
         HttpApiClient elasticClient = new HttpApiClient()
         def rtn = [success: false, data: [:], errors:[:]]
         def config = getConnectionConfig()

         if (config.elasticsearch.url != "") {
             log.info("testing elasticsearch connection: ${getConnectionConfig()}")

             def testConnectionResults = elasticClient.callJsonApi(config.elasticsearch.url, null, null, "GET")
             if (testConnectionResults.success) {
                 try {
                     rtn.success = testConnectionResults.success
                     rtn.data = testConnectionResults.data.tagline
                     rtn.errors = testConnectionResults.errors as LinkedHashMap<Object, Object>
                 } catch (Exception e) {
                     log.debug("error connecting to elasticsearch: ${rtn.errors}")
                 } finally {
                     elasticClient.shutdownClient()
                 }
             }
         }
         return rtn
    }

     static getQuery(Boolean asMap = false) {
         def queryString = '''
    {
      "query": {
        "bool": {
          "filter": [
            {
              "range": {
                "ts": {
                  "gte": "now-30d/d"
                }
              }
            },
            {
              "range": {
                "usedStorage": {
                  "gte": 1
                }
              }
            },
            {
              "range": {
                "usedMemory": {
                  "gte": 1
                }
              }
            },
            {
              "range": {
                "cpuUsage": {
                  "gte": 1
                }
              }
            }
          ]
        }
      },
      "_source": ["objectId", "usedStorage", "usedMemory", "cpuUsage"],
      "aggs": {
        "group_by_objectId": {
          "terms": {
            "field": "objectId"
          },
          "aggs": {
            "avg_usedStorage": {
              "avg": {
                "field": "usedStorage"
              }
            },
            "avg_usedMemory": {
              "avg": {
                "field": "usedMemory"
              }
            },
            "avg_cpuUsage": {
              "avg": {
                "field": "cpuUsage"
              }
            }
          }
        }
      },
      "size": 100
    }
    '''
         def queryStringMap = [:]

         switch(asMap) {
             case true:
                try {
                    queryStringMap = new JsonSlurper().parseText(queryString)
                } catch (Exception e) {
                    log.debug("error converting query string to map: ${e}")
                }
                return queryStringMap
                break

            case false:
                return queryString
                break

               default:
                 return queryString
                break
        }
    }


     static executeQuery(query) {
         HttpApiClient elasticClient = new HttpApiClient()
         def elasticUrl = getConnectionConfig().elasticsearch.url

         def requestOptions = new HttpApiClient.RequestOptions()
         requestOptions.body = query

         def rtn = [success: false, data: [:], errors: [:]]
         if (testConnection().success) {
             def elasticResults = elasticClient.callJsonApi(elasticUrl, "/stats.*/_search", requestOptions, "POST")
             log.info("retrieving report data from elasticsearch...")
             if (elasticResults.success) {
                 log.info("elasticResults: ${elasticResults}")
                 def buckets = elasticResults.data.aggregations.group_by_objectId.buckets
                 try {
                     rtn.success = elasticResults.success
                     buckets.each { bucket ->
                         def result = [
                                 id          : bucket.key,
                                 used_storage: bucket.avg_usedStorage.value,
                                 used_memory : bucket.avg_usedMemory.value,
                                 used_cpu    : bucket.avg_cpuUsage.value
                         ]
                         rtn.data << result
                     }
                     rtn.errors = elasticResults.errors as LinkedHashMap<Object, Object>
                 } catch (Exception e) {
                     log.debug("error getting results from elasticsearch: ${e}")
                 } finally {
                     elasticClient.shutdownClient()
                 }
             }
             return rtn
         }
     }
}
