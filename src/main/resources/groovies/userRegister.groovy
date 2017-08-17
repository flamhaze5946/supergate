package groovies

import com.bestv.supergate.filter.AbstractFilter

class UserRegisterRequest extends AbstractFilter
{

    /**
     * @see AbstractFilter#loadEnvironment()
     */
    void loadEnvironment() {

        setServiceCode("b001.001.001");
        setAppName("bizprod");
        setServiceInterface("com.bestv.bizprod.common.service.api.UserManageFacade");
        setMethodName("createUser");
        setClassInfo("request", "com.bestv.bizprod.common.service.api.request.UserCreateRequest");
        linkParameter("request.password", "password");
        linkParameter("request.phoneNo", "phoneNo");
        linkParameter("request.nickname", "nickname");
    }
}