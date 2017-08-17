package groovies

import com.bestv.supergate.filter.AbstractFilter

class BindViewCodeAndRoleId extends AbstractFilter
{

    /**
     * @see AbstractFilter#loadEnvironment()
     */
    void loadEnvironment() {

        setServiceCode("b001.001.006");
        setAppName("bizprod");
        setServiceInterface("com.bestv.bizprod.common.service.api.UserManageFacade");
        setMethodName("bindViewCodeAndUserId");
        linkParameter("viewCode", "viewCode");
        linkParameter("phoneNo", "phoneNo")
    }
}