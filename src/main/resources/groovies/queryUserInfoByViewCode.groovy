package groovies

import com.bestv.supergate.filter.AbstractFilter

class ViewCodeFunction extends AbstractFilter
{
    /**
     * @see AbstractFilter#loadEnvironment()
     */
    void loadEnvironment() {

        setServiceCode("b001.001.005");
        setAppName("bizprod");
        setServiceInterface("com.bestv.bizprod.common.service.api.UserQueryFacade");
        setMethodName("queryUserInfoByViewCode");
        linkParameter("viewCode", "viewCode");
    }
}