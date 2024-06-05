package groovy.com.morpheusdata.uthman

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.uthman.ServerResourceUtilReportPlugin
import com.morpheusdata.uthman.ServerResourceUtilReportProvider
import com.morpheusdata.uthman.util.DateTimeUtils
import spock.lang.Specification
import spock.lang.Subject

class SpockTest extends Specification {
    @Subject
    ServerResourceUtilReportProvider reportProvider

    MorpheusContext context
    ServerResourceUtilReportPlugin plugin
    DateTimeUtils utils

    void setup() {
        context = Mock(MorpheusContext)
        plugin = Mock(ServerResourceUtilReportPlugin)
        utils = Mock(DateTimeUtils)

        reportProvider = new ServerResourceUtilReportProvider(plugin, context)


    }

    void "getServiceProperties"() {
        given:
            def services = reportProvider.getProperties()
        when:
            services.properties.size() > 1
        then:
            println """ ${services.properties} """
    }

    void "testElasticQuery"() {
        given:
            def elasticQuery = reportProvider.getElasticQuery()
        when:
            def currentTime = utils.getCurrentDateTime(true)
            def ninetyDayInterval = utils.getDateTimeRef("90DAYS")

            elasticQuery.query.bool.filter.range.lastUpdated.lte = currentTime
            elasticQuery.query.bool.filter.range.lastUpdated.gte = ninetyDayInterval
        then:
            def results = reportProvider.getElasticResponse(elasticQuery)
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
        reportProvider

        then:
        println """
                                        [ DEBUG ]
                  -----------------------------------------------------------
 
               class:    ${reportProvider.class}

               name:    ${reportProvider.name}
           
    morpheusContext:    ${reportProvider.morpheusContext}
           morpheus:    ${reportProvider.morpheus}
           
              props:    ${reportProvider.properties}

"""

    }
}