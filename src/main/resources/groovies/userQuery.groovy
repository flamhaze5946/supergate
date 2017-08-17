package groovies

import com.bestv.supergate.filter.AbstractFilter

class UserQuery extends AbstractFilter
{

    /**
     * @see AbstractFilter#loadEnvironment()
     */
    void loadEnvironment() {

        setServiceCode("b001.001.004");
        setAppName("bizprod");
        setServiceInterface("com.bestv.bizprod.common.service.api.UserQueryFacade");
        setMethodName("queryUserById");
        linkParameter("userId", "userId");
    }
}