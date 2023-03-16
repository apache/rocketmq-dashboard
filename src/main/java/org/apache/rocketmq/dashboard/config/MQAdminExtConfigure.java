/**
 * 
 */
package org.apache.rocketmq.dashboard.config;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * @author dotla
 *
 */
@Configuration
public class MQAdminExtConfigure {
	
	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	MQAdminExt mqAdminExt(@Autowired GenericObjectPool<MQAdminExt> mqAdminExtPool) throws Exception {
		return mqAdminExtPool.borrowObject();
	}

}
