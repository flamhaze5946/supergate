package groovies

import com.bestv.supergate.filter.AbstractFilter

class NormalLogin extends AbstractFilter
{

    /**
     * @see AbstractFilter#loadEnvironment()
     */
    void loadEnvironment() {

        setServiceCode("b001.001.002");
        setAppName("bizprod");
        setServiceInterface("com.bestv.bizprod.common.service.api.UserManageFacade");
        setMethodName("normalLogin");
        setClassInfo("request", "com.bestv.bizprod.common.service.api.request.NormalLoginRequest");
        linkParameter("request.password", "password");
        linkParameter("request.phoneNo", "phoneNo");
    }
}