package groovies

import com.bestv.supergate.filter.AbstractFilter

class UpdateUser extends AbstractFilter
{

    /**
     * @see AbstractFilter#loadEnvironment()
     */
    void loadEnvironment() {

        setServiceCode("b001.001.003");
        setAppName("bizprod");
        setServiceInterface("com.bestv.bizprod.common.service.api.UserManageFacade");
        setMethodName("updateUser");
        setClassInfo("request", "com.bestv.bizprod.common.service.api.request.UserUpdateRequest");
        linkParameter("request.avatar", "avatar");
        linkParameter("request.gender", "gender");
        linkParameter("request.nickname", "nickname");
        linkParameter("request.userId", "phoneNo");
    }
}