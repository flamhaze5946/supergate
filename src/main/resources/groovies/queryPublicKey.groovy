package groovies

import com.bestv.supergate.filter.AbstractFilter

class QueryPublicKey extends AbstractFilter
{

    void loadEnvironment() {

        setServiceCode("b000.001.001");
        setAppName("bizprod");
        setServiceInterface("com.bestv.bizprod.common.service.api.PasswordManageFacade");
        setMethodName("queryPublicKey");
        linkParameter("alias", "alias")
    }
}