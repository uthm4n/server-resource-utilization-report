package com.morpheusdata.uthman

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.util.MorpheusUtils
import com.morpheusdata.core.OptionSourceProvider
import com.morpheusdata.core.Plugin
import com.morpheusdata.uthman.ServerResourceUtilReportPlugin
import com.morpheusdata.uthman.ServerResourceUtilReportProvider
import spock.lang.Specification
import spock.lang.Subject
import groovy.json.*
import groovy.json.JsonOutput

def reportMock = new ServerResourceUtilReportProviderMock()

class ServerResourceUtilReportProviderSpec extends Specification {
    @Subject 
    ServerResourceUtilReportProvider service 
    
    MorpheusContext                               context
    ServerResourceUtilReportPlugin                 plugin
    
    void setup() {
        context = Mock(MorpheusContext)
        plugin = Mock(ServerResourceUtilReportPlugin)

        service = new ServerResourceUtilReportProvider(plugin, context)
    }
    
    void "getElasticSearchQuery"() {
        when:
            def elasticQuery = service.getElasticSearchQuery()
            def currentTime = service.getCurrentTime()
            def ninetyDayInterval = service.getDateTimeRef("90DAYS")
            
            elasticQuery.query.bool.filter.range.lastUpdated.lte = currentTime
            elasticQuery.query.bool.filter.range.lastUpdated.gte = ninetyDayInterval
        then:
            println """ ${elasticQuery} """           
    }
    
    void "getDataFromElastic"() {
        
        when:
           def elasticQuery = service.getElasticSearchQuery()
           def currentTime = service.getCurrentTime()
           def ninetyDayInterval = service.getDateTimeRef("90DAYS")
           
           elasticQuery.query.bool.filter.range.lastUpdated.lte = currentTime
           elasticQuery.query.bool.filter.range.lastUpdated.gte = ninetyDayInterval
            
        then:
            def results = getDataFromElastic(elasticQuery)
            println """

                                        [ DEBUG ELASTIC RESPONSE ]
                -----------------------------------------------------------
                   name:   ${results.class}
                   data:   ${results}

                  props:   ${results.properties}


                """                
    }
    
    void "getDebugInfo"() {
        
        when:
            def name = "Uthman"
            def getPersonalDetails = service.getPersonalDetails(name)
            def methodNames = service.getMethodNames()
        
        then:
            println """
                                        [ DEBUG ]
                  -----------------------------------------------------------
 
               class:    ${service.class}

               name:    ${service.name}
           
    morpheusContext:    ${service.morpheusContext}
           morpheus:    ${service.morpheus}
           
              props:    ${service.properties}

"""

    }
}