var popup_browser = navigator.appName;
var popup_b_version = navigator.appVersion;
var popup_version = popup_b_version.split(';');
var popup_trin_version_flag = popup_version && popup_version.length > 1;
var popup_trim_Version = popup_trin_version_flag ? popup_version[1].replace(/[ ]/g, '') : '';
var popup_uam_dataType = 'json';
var popup_uam_type = 'POST';
if (popup_browser == 'Microsoft Internet Explorer' && popup_trim_Version == 'MSIE7.0') {
    popup_uam_dataType = 'jsonp';
    popup_uam_type = 'GET';
} else if (popup_browser == 'Microsoft Internet Explorer' && popup_trim_Version == 'MSIE8.0') {
    popup_uam_dataType = 'jsonp';
    popup_uam_type = 'GET';
} else if (popup_browser == 'Microsoft Internet Explorer' && popup_trim_Version == 'MSIE9.0') {
    popup_uam_dataType = 'jsonp';
    popup_uam_type = 'GET';
}

var popup_passport_appId,popup_passport_baseUrl,popup_baseUrl,popup_publicName;
var NODE_ENV = 'production';//production生产，local开发，beta预发布
if (NODE_ENV == 'production') {
    popup_passport_appId = 'otn';
    popup_passport_baseUrl = 'https://kyfw.12306.cn/passport/';
    popup_baseUrl = 'https://kyfw.12306.cn';
    popup_publicName = '/otn';
}else if (NODE_ENV == 'beta') {
    popup_passport_appId = 'otn';
    popup_passport_baseUrl = 'http://passport.12306.cn/passport/';
    popup_baseUrl = 'http://passport.12306.cn';
    popup_publicName = '/otnyt';
}else{
    popup_passport_appId = 'otnyt';
    popup_passport_baseUrl = 'http://localhost:8881/passport/';
    popup_baseUrl = 'http://localhost:8881';
    popup_publicName = '/otnyt';
}
// 统一认证登录

var popup_passport_apptk_static = popup_passport_baseUrl + 'web/auth/uamtk-static'
var popup_passport_login = popup_passport_baseUrl + 'web/login';
// var popup_passport_captcha = popup_passport_baseUrl + 'captcha/captcha-image64?login_site=E&module=login&rand=sjrand&';
// var popup_passport_captcha_check = popup_passport_baseUrl + 'captcha/captcha-check';
var popup_passport_uamtk = popup_passport_baseUrl + 'web/auth/uamtk';
var sendMobileMsg = popup_baseUrl + popup_publicName + "/login/sendMobileMsg"; //国际手机号发送下行短信
//是否开启滑块验证
var popup_passport_uamtk = popup_passport_baseUrl + 'web/auth/uamtk';
var slide_passport_url = popup_passport_baseUrl + 'web/slide-passcode';
var check_login_passport_url = popup_passport_baseUrl + 'web/checkLoginVerify';//获取登陆前校验

var popup_is_uam_login = 'Y'; // 是否统一认证登录
var popup_is_login_passCode = 'Y' // 是否启用验证码校验登录（仅本地登录）
var popup_is_sweep_login = 'Y' // 统一认证登录情况下是否开启扫码登录
var popup_is_login = 'N' // 是否已登录


var base_uamauthclient_url = popup_baseUrl + popup_publicName + '/uamauthclient';
var base_checkUpMeg = popup_baseUrl + popup_publicName + '/login/checkUpMeg';

var apptk_tmp_control = '';
var SM4_key = 'tiekeyuankp12306';

// 滑动验证码相关
var isPasscode = false;
var isOpen = false;//工具条是否开启
var appkey = 'FFFF0N000000000085DE';
var nc = null;
var nc_token = '';
var csessionid = '';
var sig = '';
var scene = 'nc_login';

// 获取短信验证码
var popup_sms_verification = popup_passport_baseUrl + 'web/getMessageCode'
//-------
var modalType = 1
// var paste = 'N'
var popup_is_message_passCode = 'Y'//是否显示短信验证
var modalTime = 60
var set_time
var is_key_up = '';//判断是否是键盘触发
var time = 60;
var flag = true;
var timer;
var code = ''

var popup_loginCallBack = function (control_type) {
    if (!$.popup_isPop && control_type != 'control') {
        window.location.href = popup_baseUrl + popup_publicName + '/login/userLogin'
    } else {
        //弹框登录回调
        if ("Y" == popup_is_uam_login) {
            //到认证中心认证去
            $.ajax({
                type: "POST",
                url: popup_passport_uamtk,
                async: false,
                data: {
                    appid: popup_passport_appId
                },
                dataType: "jsonp",
                jsonp: "callback",
                success: function (data) {
                    // data.result_code = 91;
                    code = data.result_code;
                    // $('#login_control_msg').hide();
                    $('#login_control_app').hide();
                    $('#login_control_offline').hide();
                    $('#login_control_error').hide();
                    $('#login_control_error_down').hide();
                    if (data.result_code == 0) {
                        var apptk = data.newapptk || data.apptk;
                        //将TK种到自己的Cookies中
                        $.popup_login_control(apptk);
                        if (typeof $.pop_callback === "function") {
                            $.pop_callback();
                        }
                    } else if (data.result_code == 91) {
                        var apptk = data.newapptk || data.apptk;
                        apptk_tmp_control = apptk;

                        $('#login_control_mobile').html('请用尾号' + data.mobile + '手机号发短信666到12306。');
                        $("#modal").hide()
                        $(".mask").hide()
                        $("#down").hide();
                        $('#login_control_submit').css({ 'height': '30px', 'line-height': '20px' });
                        $('#login_control_msg').show();
                    } else if(data.result_code == 92){
                        var apptk = data.newapptk || data.apptk;
                        apptk_tmp_control = apptk;
                        $("#mobile_down").html(data.mobile);
                        $("#upStream").hide();
                        $("#down").show();
                        $("#modal").hide();
                        $(".mask").hide();
                        $("#login_control_msg").show();
                    } else if (data.result_code == 95) {
                        var apptk = data.newapptk || data.apptk;
                        apptk_tmp_control = apptk;
                        $("#modal").hide()
                        $(".mask").hide()
                        $('#login_control_app').show();
                    } else if (data.result_code == 94) {
                        var apptk = data.newapptk || data.apptk;
                        apptk_tmp_control = apptk;
                        $("#modal").hide()
                        $(".mask").hide()
                        $('#login_control_offline').show();
                    } else if (data.result_code == 97) {
                        var apptk = data.newapptk || data.apptk;
                        apptk_tmp_control = apptk;
                        // 调用是否开启滑块接口
                        $.slide_passport(false);
                    }
                },
                error: function () { }
            });
        } else {
            $('.mask').fadeOut();
            $("#login").hide();
            if ($.pop_secretStr && $.pop_start_time) {
                $.todo_submitOrderRe($.pop_secretStr, $.pop_start_time);
            }
            if (typeof $.pop_callback === "function") {
                $.pop_callback();
            }
        }
    }
}
// 初始读取conf，已登录状态，重定向
var popup_loginedCallBack = function () {
    if (!$.popup_isPop) {
        window.location.href = popup_baseUrl + popup_publicName + '/view/index.html'
    }
}
var popup_qr_appId = 'otn'
var popup_url = {
    'loginConf': popup_baseUrl + popup_publicName + '/login/conf',
    // 本地登录
    'getPassCodeNew': popup_baseUrl + popup_publicName + '/passcodeNew/getPassCodeNew?module=login&rand=sjrand&',
    'checkRandCodeAnsyn': popup_baseUrl + popup_publicName + '/passcodeNew/checkRandCodeAnsyn',
    'login': popup_baseUrl + popup_publicName + '/login/loginAysnSuggest',
    'getBanners': popup_baseUrl + popup_publicName + '/index12306/getLoginBanner',
    'loginSlide': popup_baseUrl + popup_publicName + '/slide/loginSlide',//本地登录获取滑块验证码
    // 扫码登录
    'qr': popup_baseUrl + '/passport/web/create-qr',
    'qr64': popup_baseUrl + '/passport/web/create-qr64',
    'checkqr': popup_baseUrl + '/passport/web/checkqr'
}

//验证码顶部高度
var popup_defaultPasscodeHeight = 30;
var popup_ifSuccessCode = false;

// var popup_passCodeImg = $('#J-loginImg'); // 验证码图片

var popup_ispopup_CreateQr = false;
var popup_t = null, popup_s = '-1';
// var popup_slideIsLoad = true;

var popup_isPopupLogin = true;

var forie = 'forie.html'

$(document).ready(function(){
    try {
    	var address = window.location.pathname.split('/')[3];
        if(address == 'login.html'){
        	setTimeout(function(){
                $('.login-box').focus();
            },500)
        }
	} catch (e) {
	}
	var _big_fontsize = $.common_jc_getcookie_login("_big_fontsize");
	if("1" == _big_fontsize){
		$("body").addClass('is-caring');
	}
})
jQuery.extend({
    pop_secretStr: "",
    pop_start_time: "",
    popup_isPop: true,
    pop_callback: function () { },
    popup_show_login_error: function (msg) {
        if ('验证码错误！' != msg && '请选择验证码！' != msg) {
            $('#J-password').val('');
        }
        if (msg && msg.indexOf('重新设置密码') > -1) {
            msg = msg.substring(0, msg.indexOf('重新设置密码')) +
                '<a href="' + popup_baseUrl + popup_publicName + '/forgetPassword/initforgetMyPassword" style="color:#3B99FC">' +
                '重新设置密码' +
                '</a>' + msg.substring(msg.indexOf('重新设置密码') + 6);
        }
        $('#J-login-error').show().find('span').html(msg);
        try {
            setTimeout(function () {
                $('#J-login-error').attr('aria-label', msg);
                $('#J-login-error').focus();
            }, 1000);
        } catch (e) {

        }
    },
    common_jc_getcookie_login:function(name){
 	   var cookie_start = document.cookie.indexOf(name);
 	   var cookie_end = document.cookie.indexOf(";", cookie_start);
 	   return cookie_start == -1 ? '' : unescape(document.cookie.substring(
 				cookie_start + name.length + 1,
 				(cookie_end > cookie_start ? cookie_end
 						: document.cookie.length)));	
 	   
 	   
 	},
    popup_hide_login_error: function () {
        setTimeout(function () {
            $('#J-login-error').hide().find('span').html('');
        }, 1000);
    },
    popup_is_allow_msg: function (deliverData) {
        var randCode = code == 91 ? $('#up_msg_code').val():$('#up_msg_code_down').val();
        var base_checkUpMeg_data = {};
        if (deliverData.flag) {
            base_checkUpMeg_data['sessionId'] = deliverData.sessionId;
            base_checkUpMeg_data['sig'] = deliverData.sig;
            base_checkUpMeg_data['if_check_slide_passcode_token'] = deliverData.if_check_slide_passcode_token;
            base_checkUpMeg_data['scene'] = deliverData.scene;
            base_checkUpMeg_data['tk'] = deliverData.tk;
        } else {
            base_checkUpMeg_data['tk'] = deliverData;
            base_checkUpMeg_data['randCode'] = randCode;
        }
        $.ajax({
            type: "POST",
            async: false,
            url: base_checkUpMeg,
            data: base_checkUpMeg_data,
            datatype: "json",
            success: function (response) {
                var data = response.data;
                if (data) {
                    $.popup_login_control(base_checkUpMeg_data);
                } else {
                    $('#login_control_error').show();
                    $('#login_control_error_down').show();
                }
            },
            error: function () { }
        });
    },
    popup_login_control: function (apptk) {
        if (!$.popup_isPop) {
            window.location.href = popup_baseUrl + popup_publicName + '/login/userLogin'
        } else {
            $.ajax({
                type: "POST",
                async: false,
                url: base_uamauthclient_url,
                data: {
                    tk: typeof apptk == 'object' ? apptk.tk : apptk
                },
                datatype: "json",
                success: function (data) {
                    if (data.result_code == 0) {
                        $('.mask').fadeOut();
                        $("#login").hide();
                        if ($.pop_secretStr && $.pop_start_time) {
                            $.todo_submitOrderRe($.pop_secretStr, $.pop_start_time);
                        }
                    }
                },
                error: function () { }
            });
        }
    },

    // 统一认证登录
    popup_loginForUam: function (_formData) {
        var formData = {};
        if (_formData) {
            formData = _formData
        } else {
            var randCode = '';
            var obj = $('#J-passCodeCoin div');

            for (var i = 0; i < obj.length; i++) {
                randCode += $(obj[i]).attr('randcode') + ',';
            }
            randCode = randCode.substring(0, randCode.length - 1);


            formData['username'] = $('#J-userName').val().trim();
            formData['password'] = '@' + encrypt_ecb($('#J-password').val(), SM4_key);
            formData['appid'] = popup_passport_appId;
            formData['answer'] = randCode;

        }
        isOpen = typeof(EsdToolbar)=='object'&&EsdToolbar.isOpen
        $.ajax({
            crossDomain: true,
            url: popup_passport_login,
            data: formData,
            dataType: popup_uam_dataType,
            type: popup_uam_type,
            timeout: 10000,
            xhrFields: { withCredentials: true },
            headers: {
                'isPasswordCopy': paste,
                'appFlag':isOpen?'otnLove':''
            },
            success: function (data) {
                // data.result_code = 91;
                if (data.result_code == 0) {
                    $.popup_hideCommonLogin();
                    $("#login_slide_mask").hide();
                    $("#modal").hide();
                    // 成功回调
                    popup_loginCallBack()
                } else if (data.result_code == 91) {
                    popup_loginCallBack('control')
                } else if (data.result_code == 92) {
                    popup_loginCallBack('control')
                } else if (data.result_code == 94) {
                    popup_loginCallBack('control')
                } else if (data.result_code == 95) {
                    popup_loginCallBack('control')
                } else if (data.result_code == 97) {
                    popup_loginCallBack('control');
                } else if (data.result_code == 101) {
                    var updatePwdMsg = '您的密码很久没有修改了，为降低安全风险，请您' +
                        '<a href="' + popup_baseUrl + popup_publicName + '/forgetPassword/initforgetMyPassword" style="color:#3B99FC">' +
                        '重新设置密码' + '</a>' + '后再登录。';
                    $.popup_show_login_error(updatePwdMsg)
                    $('#J-passCodeCoin').html('');
                } else {
                    $.message_show(data.result_message);
                }
            },
            error: function () {
                $.popup_hideCommonLogin();
                $("#login_slide_mask").hide();
                $("#modal").hide();
            }
        });

    },
    // 消息展示
    message_show: function (messages) {
        if (modalType == 1) {
            $.popup_show_login_error(messages)
            $('#J-passCodeCoin').html('');
            $("#modal").hide();
            $("#login_slide_mask").hide();
            $("#login").show();
            try {
                setTimeout(function () {
                    $("#login").focus();
                }, 1000);
            } catch (e) {

            }
        } else {
            $("#message p").html(messages);
            $("#message").show();
            try {
                setTimeout(function () {
                    $("#message").attr('aria-label', messages);
                    $("#message").focus();
                }, 1000);
            } catch (e) {

            }
        }
    },
    // 本地登录+验证码
    popup_loginForLocation_passcode: function (formData) {

        $.ajax({
            url: popup_url.login,
            data: formData,
            type: 'POST',
            timeout: 10000,
            success: function (response) {
                var data = response.data;
                if (data && data.loginCheck == 'Y') {
                    $.popup_hideCommonLogin();
                    $("#login_slide_mask").hide();
                    $("#modal").hide();
                    // 成功回调
                    popup_loginCallBack()
                } else if (data && data.message) {
                    $.message_show(data.message);
                } else if (response.messages) {
                    $.message_show(response.messages);
                    if (popup_is_uam_login == 'Y') {
                        $.message_show(response.messages);
                    } else {
                        $.message_show(response.messages[0]);
                    }
                } else {
                    $.popup_hideCommonLogin();
                }
            },
            error: function (e) {
                $.popup_hideCommonLogin();
                $(".mask").css({ 'z-index': 0 });
                $('.#login').fadeOut();
                $(".mask").fadeOut();
                $("#login_slide_mask").hide();

            }
        });
    },
    // 本地登录
    popup_loginForLocation: function () {
        $.ajax({
            url: popup_url.login,
            data: {
                'loginUserDTO.user_name': $('#J-userName').val(),
                'userDTO.password': '@' + encrypt_ecb($('#J-password').val(), SM4_key)
            },
            type: 'POST',
            timeout: 10000,
            success: function (response) {
                var data = response.data;
                if (data && data.loginCheck == 'Y') {
                    $.popup_hideCommonLogin();
                    // 成功回调
                    popup_loginCallBack()
                } else if (data && data.message) {
                    // ==========================================
                    $.popup_show_login_error(data.message)
                } else if (response.messages) {
                    // ==========================================
                    $.popup_show_login_error(response.messages)
                } else {
                    $.popup_hideCommonLogin();
                }
            },
            error: function (e) {
                $.popup_hideCommonLogin();
            }
        });
    },
    popup_hideCommonLogin: function () {
        $('#J-userName').val('');
        $('#J-password').val('');
        $('#J-login-error').hide();
        paste = 'N'
    },
    popup_showLoginType: function (index) {
        $('#J-loginImgArea').hide();
        $('.lgcode-error').hide();
        $('.lgcode-loading').hide();
        $('.lgcode-loading img').hide();
        $('.lgcode-success').hide();
        if (0 == index) {
            $('#J-loginImgArea').show();    //show code
        } else if (1 == index) {
            $('.lgcode-error').show();  // code error
        } else if (2 == index) {
            $('.lgcode-success').show(); // code success
        } else if (3 == index) {
            $('.lgcode-loading').show();    // loading code
            $('.lgcode-loading img').show();
        }
    },
    popup_getClickPos: function (e) {
        var xPage = (navigator.appName == 'Netscape') ? e.pageX : e.clientX + (document.documentElement.scrollLeft || window.pageXOffset || document.body.scrollLeft);
        var yPage = (navigator.appName == 'Netscape') ? e.pageY : e.clientY + (document.documentElement.scrollTop || window.pageYOffset || document.body.scrollTop);
        identifyImage = document.getElementById('J-loginImg');
        img_x = $.popup_locationLeft(identifyImage);
        img_y = $.popup_locationTop(identifyImage);

        var xPos = xPage - img_x;
        var yPos = yPage - img_y - popup_defaultPasscodeHeight;
        if (xPos > 0 && yPos > 0) {
            var html = '<div randCode="' + xPos + ',' + yPos + '" class="lgcode-active" style="top: ' + (yPos + 16) + 'px; left: ' + (xPos - 13) + 'px;"></div>';
            $('#J-passCodeCoin').append(html);
        }
        $('.lgcode-active').click(function (e) {
            $(this).remove();
            e.stopPropagation();
        });
    },
    popup_locationLeft: function (element) {
        offsetTotal = element.offsetLeft;
        scrollTotal = 0;
        if (element.tagName != 'BODY') {
            if (element.offsetParent != null) {
                return offsetTotal + scrollTotal + $.popup_locationLeft(element.offsetParent);
            }
        }
        return offsetTotal + scrollTotal;
    },
    popup_locationTop: function (element) {
        offsetTotal = element.offsetTop;
        scrollTotal = 0;
        if (element.tagName != 'BODY') {
            if (element.offsetParent != null) {
                return offsetTotal + scrollTotal + $.popup_locationTop(element.offsetParent);
            }
        }
        return offsetTotal + scrollTotal;
    },
    // 初始化登录
    popup_initLogin: function (isPopupLogin) {

        var popup_browser_flag = false;
        if (popup_browser == "Microsoft Internet Explorer" && popup_trim_Version == "MSIE7.0") {
            popup_browser_flag = true;
        } else if (popup_browser == "Microsoft Internet Explorer" && popup_trim_Version == "MSIE6.0") {
            popup_browser_flag = true;
        }
        if (popup_browser_flag) {
            location.href = forie; //浏览器升级页面
            return;
        }

        $.popup_isPop = isPopupLogin;
        popup_isPopupLogin = isPopupLogin;

        $.popup_hideCommonLogin();
        $.popup_showLoginType(3); // loading code

        // 获取配置信息
        $.popup_getConf()
        $.isKeyUp()

        $('#J-userName').focus(function () {
            $.popup_hide_login_error();
        })

        $('#J-password').focus(function () {
            $.popup_hide_login_error();
        })

        // 注册切换登录方式事件
        $.popup_switchLoginWay()

        // 注册刷新二维码事件
        $.popup_refreshQrCode()

        //登录控制
        $('#login_control_submit').off('click').click(function () {
            $.popup_is_allow_msg(apptk_tmp_control);
        });
        $("#login_control_submit_down").off("click").click(function () {
            $.popup_is_allow_msg(apptk_tmp_control);
            $.clearTimer('#verification_code_down')
            $("#mobile_msg_err").hide();
        });
        $("#login_control_cancel").off("click").on("click", function () {
            $('div[name="login_control_mask"]').hide();
            $.clearTimer('#verification_code_down')
            $("#mobile_msg_err").hide();
        });
        $('#up_msg_code').on('input', function () {
            $('#login_control_error').hide();
        });
        $("#up_msg_code_down").on("input", function () {
            $("#login_control_error_down").hide();
          });
        $('a[name="login_control_close"]').click(function () {
            $('div[name="login_control_mask"]').hide();
        });
        $("#verification_code_down").off("click").on("click", function () {
            var obj = {
                type:'login',
                tk:apptk_tmp_control.flag?apptk_tmp_control.tk:apptk_tmp_control
            }
            $.getAreaCode(obj,'#verification_code_down');
            $("#mobile_msg_err").hide();
        })
    },
    // 获取配置信息
    popup_getConf: function () {
        $.ajax({
            url: popup_url.loginConf,
            type: 'POST',
            timeout: 10000,
            success: function (response) {
                var data = response.data;
                if (data) {
                    popup_is_uam_login = data.is_uam_login;
                    popup_is_login_passCode = data.is_login_passCode;
                    popup_is_sweep_login = data.is_sweep_login;
                    popup_is_login = data.is_login;
                    if (data.is_message_passCode == undefined || data.is_message_passCode == '' || data.is_message_passCode == 'Y') {
                        popup_is_message_passCode = 'Y'
                    } else {
                        popup_is_message_passCode = 'N'
                    }
                    $.popup_isLogin()
                    $("#login_short_message").css({
                        'z-index': 100000
                    })
                    $("#login_slide_box").css({
                        'z-index': 100000
                    })
                    $('#login_control_submit').css({ 'height': '30px', 'line-height': '20px' });
                }
            },
            error: function (error) {
                // error
            }
        });
    },
    popup_isLogin: function () {
        if (popup_is_uam_login == 'Y') {
            $(".login-code-select").show();
            if (popup_isPopupLogin) {
                $.popup_uamIsShowQr();
            } else {
                $.popup_uamIsLogin()
            }
        } else {
            // 本地登录
            $(".login-code-select").hide();
            if (popup_is_login == 'Y') {
                popup_loginedCallBack();
            } else {
                // 隐藏扫码登录入口
                $.popup_hideQrCode()

                $('.login-account').show();

                // 本地登录
                if (popup_is_login_passCode == 'Y') {
                    // 显示验证方式
                    // $(".login-code-select").show();
                    if (popup_is_message_passCode == 'Y') {
                        $("#verification li:last").show();
                    } else {
                        $("#verification li:last").hide();
                        $("#verification li").css({
                            'width': '320px',
                            'float': 'none',
                            'margin-right': '0px'
                        });
                    }
                } else {
                    // 隐藏验证方式
                    // $(".login-code-select").hide();
                    // 重置登录窗口居中
                    $.popup_resetLoginBox();
                }

                // 表单验证
                $.popup_validate()
            }
        }
    },
    // 重置登录窗口居中
    popup_resetLoginBox: function () {
        //设置登录窗口局中
        var loginBox = $('.login-panel .login-box');
        loginBox.css('margin-top', -loginBox.outerHeight() / 2)
    },
    // 是否已登录，仅限统一认证登录
    popup_uamIsLogin: function () {
        $.ajax({
            url: popup_passport_apptk_static,
            data: { 'appid': popup_passport_appId },
            type: 'POST',
            xhrFields: {
                withCredentials: true
            },
            timeout: 10000,
            success: function (response) {
                if (response.result_code == '0') {
                    // 已经登录
                    popup_loginedCallBack();
                } else {
                    $.popup_uamIsShowQr();
                }
            },
            error: function (error) {
            }
        });
    },
    popup_uamIsShowQr: function () {
        if (popup_is_sweep_login == 'Y') {
            // 显示扫码登录入口
            // $.popup_showQrCode()

            // loading
            $('#J-login-code-loading').show();
            $('#J-login-code-con').hide()
            $.popup_hideQrError();

            // 创建登录二维码
            // $.popup_createQr()
        } else {
            // 隐藏扫码登录入口
            $.popup_hideQrCode()
            $('.login-account').show();
            // 获取统一认证登录验证码
            // $.popup_createPassCode()
        }

        // 表单验证
        $.popup_validate()
    },
    // 扫码登录-获取二维码接口
    popup_createQr: function () {
        $.ajax({
            url: popup_url.qr64,
            data: {
                appid: popup_qr_appId
            },
            type: 'POST',
            timeout: 10000,
            success: function (data) {
                if (data && data.result_code === '0' && data.image) {
                    $('#J-qrImg').attr('src', 'data:image/jpg;base64,' + data.image);

                    $('#J-login-code-loading').hide();
                    $('#J-login-code-con').show();
                    $('#J-code-error-mask').hide();
                    $('#J-code-error').hide();

                    popup_t = null
                    popup_s = -1

                    popup_t = setInterval(function () {
                        if (popup_s == '2' || popup_s == '3') {
                            clearInterval(popup_t)
                        } else {
                            // 轮询调用二维码检查接口，直至返回状态为2：登录成功，（已识别且已授权）、3：已失效
                            $.popup_checkQr(data.uuid)
                        }
                    }, 1000)

                } else {
                    // error
                }
            },
            error: function (error) {
                // error
            }
        })
    },
    // 扫码登录-轮询调用二维码检查接口
    popup_checkQr: function (uuid) {
        $.ajax({
            url: popup_url.checkqr,
            data: {
                RAIL_DEVICEID: $.cookie("RAIL_DEVICEID"),
                RAIL_EXPIRATION: $.cookie("RAIL_EXPIRATION"),
                uuid: uuid,
                appid: popup_qr_appId
            },
            type: 'POST',
            timeout: 10000,
            success: function (data) {
                if (data) {
                    popup_s = data.result_code
                    $.popup_tipsQrInfo(parseInt(data.result_code))
                }
            },
            error: function (err) {

            }
        })
    },
    // 显示二维码已失效
    popup_showQrError: function (msg) {
        $('#J-code-error-mask').show()
        $('#J-code-error').show()
        $('#J-code-error').find('p').html(msg)
    },
    // 隐藏二维码已失效
    popup_hideQrError: function () {
        $('#J-code-error-mask').hide();
        $('#J-code-error').hide();
    },
    // 显示二维码loading效果
    popup_showQrLoading: function () {
        $('.login-code-loading').show();
        $('.login-code-con').hide();
    },
    // 隐藏二维码loading效果
    popup_hideQrLoading: function () {
        $('.login-code-loading').hide();
        $('.login-code-con').show();
    },
    // 二维码上的信息提示
    popup_tipsQrInfo: function (resCode) {

        // 0：未识别、
        // 1：已识别，暂未授权（未点击授权或不授权）、
        // 2：登录成功，（已识别且已授权）、
        // 3：已失效、
        // 5系统异常
        var codeErrorMask = $('#J-code-error-mask')
            , codeError = $('#J-code-error')
            , codeTips = $('#J-login-code-con')
            , codeTipsSuccess = $('#J-login-code-success')

        if (resCode == 0) {
            codeErrorMask.hide()
            codeError.hide()
        } else {
            codeErrorMask.show()
            codeError.show()

            switch (resCode) {
                case 1:
                    codeTips.hide()
                    codeTipsSuccess.removeClass('hide')
                    break;
                case 2:
                    codeTips.hide()
                    codeTipsSuccess.removeClass('hide')
                    // 成功回调
                    popup_loginCallBack()
                    break;
                case 3:
                    codeTips.show()
                    codeError.find('p').html('二维码已失效')
                    codeError.find('a').show()
                    codeTipsSuccess.addClass('hide')
                    break;
                case 5:
                    codeTips.show()
                    codeError.find('p').html('系统异常')
                    codeError.find('a').show()
                    codeTipsSuccess.addClass('hide')
                    break;
                default:
                    codeError.find('p').html('二维码已失效')
                    codeError.find('a').show()

                    codeTipsSuccess.addClass('hide')
            }
        }
    },
    // 表单验证
    popup_validate: function () {

        $('#J-login').off('click').click(function () {

            var userName = $('#J-userName').val()
            var password = $('#J-password').val()
            var mobile = /^(13[0-9])|(14[0-9])|(15[0-9])|(18[0-9])|(17[0-9])\d{8}$/;
            var tel = /^[A-Za-z]{1}([A-Za-z0-9]|[_]) {0,29}$/;
            var tel_other = /^((([a-z]|\d|[!#\$%&'\*\+\-\/=\?\^_`{\|}~]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])+(\.([a-z]|\d|[!#\$%&'\*\+\-\/=\?\^_`{\|}~]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])+)*)|((\x22)((((\x20|\x09)*(\x0d\x0a))?(\x20|\x09)+)?(([\x01-\x08\x0b\x0c\x0e-\x1f\x7f]|\x21|[\x23-\x5b]|[\x5d-\x7e]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(\\([\x01-\x09\x0b\x0c\x0d-\x7f]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]))))*(((\x20|\x09)*(\x0d\x0a))?(\x20|\x09)+)?(\x22)))@((([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])*([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])))\.)+(([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])*([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])))$/i;
            var mobileInter = /[0-9-]/
            if (!userName) {
                $.popup_show_login_error('请输入用户名！')
                return false;
            }
            if (!password) {
                $.popup_show_login_error('请输入密码！')
                return false;
            }
            if (password && password.length < 6) {
                $.popup_show_login_error('密码长度不能少于6位！')
                return false;
            }
            

            popup_ifSuccessCode = true;

            $.popup_login();
        });
    },


    //判断是否是键盘操作
    isKeyUp: function () {
        var loginBtn = $("#J-login")//$("#J-btn-login") || 
        loginBtn.trigger("mousedown")
        loginBtn.trigger("keyup")
        loginBtn.on('mousedown', function () {
            is_key_up = false
            modalType = 1
        })
        loginBtn.on('keyup', function () {
            is_key_up = true
            modalType = 0
        })
    },
    // 操作验证方式
    keyboard_operation: function () {

        if (is_key_up == true) {
            $("#verification li:first").attr('type', '0').addClass('active')
            $("#verification li:first a").html('短信验证')
            $("#verification li:last").attr('type', '1').removeClass('active')
            $("#verification li:last a").html('滑块验证')
            $("#short_message").show();
            $("#slide").hide();
        } else {
            $("#verification li:first").attr('type', '1').addClass('active')
            $("#verification li:first a").html('滑块验证').removeClass('active')
            $("#verification li:last").attr('type', '0')
            $("#verification li:last a").html('短信验证')
            $("#short_message").hide();
            $("#slide").show();
        }
        $.popup_selectLoginMode()
    },
    // 登录
    popup_login: function () {
        if (popup_is_uam_login == 'Y') {
            // 拉取统一认证登录
            $.ajax({
                url: check_login_passport_url,
                data: {
                    username: $('#J-userName').val().trim(),
                    appid: popup_qr_appId
                },
                type: 'POST',
                timeout: 10000,
                success: function (data) {
                    if (data) {
                        if (data.login_check_code == '0') {
                            var formData = {}
                            formData['sessionId'] = '';
                            formData['sig'] = '';
                            formData['if_check_slide_passcode_token'] = '';
                            formData['scene'] = '';
                            formData['checkMode'] = ''
                            formData['randCode'] = $("#code").val().trim()
                            formData['username'] = $('#J-userName').val().trim();
                            formData['password'] = '@' + encrypt_ecb($('#J-password').val().trim(), SM4_key);
                            formData['appid'] = popup_passport_appId;
                            $.popup_loginForUam(formData);
                        } else if (data.login_check_code == '1') {
                            $("#verification li:last").show();
                            $.keyboard_operation();
                            // $.popup_selectLoginMode();
                        } else if (data.login_check_code == '2') {
                            $("#verification li:last").hide();
                            $("#verification li").css({
                                'width': '320px',
                                'float': 'none',
                                'margin-right': '0px'
                            });
                            $.popup_selectLoginMode();
                        } else if (data.login_check_code == '3') {
                            $("#verification li:last").show();
                            $("#verification li:first").hide();
                            $("#verification li").css({
                                'width': '320px',
                                'float': 'none',
                                'margin-right': '0px'
                            }).addClass('active');
                            $(".mask").show();
                            modalType = 0
                            $.popup_selectLoginMode();
                            $.verificationClick();
                        }
                        // else {
                        //     $("#verification li:last").hide();
                        //     $("#verification li").css({
                        //         'width': '320px',
                        //         'float': 'none',
                        //         'margin-right': '0px'
                        //     });
                        //     $.popup_selectLoginMode();
                        // }
                    }
                },
                error: function (err) {

                }
            })

        } else {
            if (popup_is_login_passCode == 'Y') {
                // 选择登陆验证方式
                $.popup_selectLoginMode();
            } else {
                // 拉取本地登录
                $.popup_loginForLocation()
            }
        }
    },
    // 选择登陆验证方式
    popup_selectLoginMode: function () {
        $("#login").fadeOut();
        $("#login_slide_mask").show();
        if ($("#login").length == 0) {
            $('#modal').css("top", '35%').css("left", '50%').show().focus();
        } else {
            $('#modal').css("top", ($(window).height() - $('#modal').height()) / 2 + $(window).scrollTop() + "px")
                .css("left", ($(window).width() - $('#modal').width()) / 2 + $(window).scrollLeft() + "px").show().focus();
        }
        if (modalType == 1) {
            $.slide_passport(true)
        } else {
            $("#login_slide_mask").show();
            $("#short_message").show();
            $("#slide").hide();
            // $("#login_short_message").show();
            $("#id_card").val('')
            // $("#id_card").focus();
            $("#code").val('')
            $.smsVerification();
        }
        $("#short_message_close").click(function () {
            if ($("#login").length == 0) {
                $("#login_slide_mask").hide();
            }
            $("#modal").fadeOut();
            $("#login").show();
            $(".mask").hide();
            try {
                setTimeout(function () {
                    $("#login").focus();
                })
            } catch (error) {

            }
            $("#message").hide();
            $("#verification_code").addClass('btn-disabled')
            modalType = 1
            clearTimeout(set_time)
            $("#verification_code").off('click')
            modalTime = 60;
            $("#verification_code").html('获取验证码')
        });
        $("#verification li").bind('click').on('click', function () {
            $(this).addClass('active').siblings().removeClass('active')
            modalType = $(this).attr('type')
            if (modalType == 1) {
                $("#slide").show();
                $("#short_message").hide();
                clearTimeout(set_time)
                $("#verification_code").off('click')
                modalTime = 60;
                $("#verification_code").html('获取验证码')
                $.slide_passport(true)
            } else {
                $("#short_message").show();
                $("#slide").hide();
                $("#id_card").focus().val('')
                $("#code").val('')
                $.smsVerification()
            }
        })
        $("#login_close").click(function () {
            $("#verification li:first").addClass('active').siblings().removeClass('active');
            modalType = 1
        })
        if (modalType == 1) {
            $("#verification li:first").addClass('active').siblings().removeClass('active');
            $("#slide").show();
            $("#short_message").hide();
        }

    },
    // 显示扫码登录
    popup_showQrCode: function () {
        $('.login-box').removeClass('login-box-account')
        $(".login-hd li:eq(1)").removeClass('login-hd-code')
        $('.login-code').show();
    },
    // 隐藏扫码登录
    popup_hideQrCode: function () {
        $('.login-box').addClass('login-box-account');
        $(".login-hd li").removeClass().addClass('login-hd-account')
        $(".login-hd li:eq(1)").addClass('login-hd-code')
        $('.login-code').hide();
    },
    // 刷新二维码
    popup_refreshQrCode: function () {
        $('.code-error .btn').off('click').click(function () {
            $('#J-login-code-loading').show();
            $('#J-login-code-con').hide();
            $.popup_hideQrError()
            // 生成二维码
            $.popup_createQr()
        })
    },
    // 切换登录方式
    popup_switchLoginWay: function () {

        // 扫码登录
        $('.login-hd-account a').off('click').click(function () {
            $('#J-login-code-loading').show();
            $('#J-login-code-con').hide();
            // 隐藏已失效二维码
            $.popup_hideQrError()

            // 隐藏扫码成功
            $('#J-login-code-success').addClass('hide');

            $.popup_hideCommonLogin()

            if (popup_t) {
                clearInterval(popup_t)
                popup_t = null
                popup_s = -1
            }

            // 生成二维码
            $.popup_createQr()

        })

        // 账号登录
        $('.login-hd-code a').off('click').click(function () {
            if (popup_t) {
                clearInterval(popup_t)
                popup_t = null
                popup_s = -1
            }
        })
    },

    popup_clearInterval: function () {
        if (popup_t) {
            clearInterval(popup_t)
            popup_t = null
            popup_s = -1
        }
    },
    getBanners: function () {
        // 轮播图
        $.ajax({
            url: popup_url.getBanners,
            type: 'GET',
            timeout: 10000,
            dataType: 'text',
            success: function (response) {
                if (response) {
                    var data = JSON.parse(response);
                    $.each(data.data.index_banner_url, function (index, value) {
                        var thtml = value.src ? "<a href=\"" + value.src + "\"></a>" : "<a style='cursor:auto;' href='javascript:void(0)'></a>";
                        $('.loginSlide .bd ul').append("<li style=\"background: url(" + value.url + ") center center no-repeat;\">" + thtml + "</li>");
                    })
                    $(".loginSlide").slide({
                        titCell: ".hd ul",
                        mainCell: ".bd ul",
                        effect: "leftLoop",
                        vis: "auto",
                        autoPlay: true,
                        autoPage: true,
                        trigger: "click",
                        interTime: "6000"
                    });
                }
            },
            error: function (error) {

            }
        });
    },

    // 是否需要滑块验证
    slide_passport: function (slideFlag) {
        var slideData = {};
        // if (slideFlag) {
        //     slideData['slideMode'] = '0';
        // } else {
        //     slideData['slideMode'] = '1';
        // }

        var slide_url
        if (popup_is_uam_login == 'Y') {
            slide_url = slide_passport_url
            slideData['slideMode'] = '1';
            slideData['appid'] = popup_qr_appId;
            slideData['username'] = $('#J-userName').val().trim();
        } else {
            slide_url = popup_url.loginSlide
        }
        $.ajax({
            url: slide_url,
            type: 'POST',
            data: slideData,
            success: function (response) {
                if (response) {
                    if (response.result_code == 0 || response.data.ifOpenLoginSlide == 'Y') {
                        if (popup_is_uam_login == 'Y') {
                            nc_token = response.if_check_slide_passcode_token;
                        } else {
                            nc_token = response.data.if_check_slide_passcode_token;
                        }
                        // 调用滑块
                        $.getSlidePasscode(nc_token, slideFlag);
                    } else if (response.result_code == 1) {
                        popup_ifSuccessCode = true;
                        $.popup_showLoginType(2); // code popup_validate success
                        // 登录
                        $.popup_loginForUam()
                    } else {
                        // 校验失败
                        // $.popup_passCodeError();
                    }
                }
            },
            error: function (error) {

            }
        });
    },
    // 滑块方法
    getSlidePasscode: function (token, initFlag) {
        nc = new noCaptcha({
            renderTo: "#J-slide-passcode",
            appkey: appkey,
            scene: scene,
            token: token,
            customWidth: 340,
            trans: { "key1": "code0" },
            elementID: ["usernameID"],
            is_Opt: 0,
            language: "zh",
            isEnabled: true,
            timeout: 3000,
            times: 5,
            apimap: {},
            callback: function (data) {
                // _hideErrorMsg();
                csessionid = data.csessionid;
                sig = data.sig;

                // // 统一认证登录 + 滑动验证
                var formData = {};
                formData['sessionId'] = csessionid;
                formData['sig'] = sig;
                formData['if_check_slide_passcode_token'] = nc_token;
                formData['scene'] = scene;
                if (initFlag) {
                    if (popup_is_uam_login == 'Y') {
                        formData['username'] = $('#J-userName').val().trim();
                        formData['password'] = '@' + encrypt_ecb($('#J-password').val().trim(), SM4_key);
                        formData['tk'] = apptk_tmp_control;
                        formData['checkMode'] = '1'
                        formData['appid'] = popup_passport_appId;
                        // 统一认证登录+验证码
                        $.popup_loginForUam(formData);
                    } else {
                        formData['loginUserDTO.user_name'] = $('#J-userName').val().trim();
                        formData['userDTO.password'] = '@' + encrypt_ecb($('#J-password').val().trim(), SM4_key);
                        // 本地登录+验证码
                        $.popup_loginForLocation_passcode(formData)
                    }
                } else {
                    formData['flag'] = true;
                    $.popup_is_allow_msg(formData);
                }

            }
        })
        nc.upLang('zh', {
            _startTEXT: "请按住滑块，拖动到最右边，完成登录",
            _yesTEXT: "验证通过",
            _error300: "哎呀，出错了，点击<a  href=\"javascript:__nc.reset()\">刷新</a>再来一次",
            _errorNetwork: "网络不给力，请<a  href=\"javascript:__nc.reset()\">点击刷新</a>",
        });
        // $.toggleSlidePasscode(true);
    },
    start: function () {

        if (modalTime == 0) {
            $("#verification_code").html("获取验证码");
            $("#verification_code").removeClass('btn-disabled').removeAttr('disabled');
            modalTime = 60;
            clearTimeout(set_time)
            $.verificationClick()
        } else {
            $("#verification_code").addClass('btn-disabled').attr('disabled', true);
            $("#verification_code").html("重新发送(" + modalTime + ")");
            modalTime--;
            clearTimeout(set_time)
            set_time = setTimeout(function () {
                $.start()
            },
                1000)
        }

    },
    verificationClick: function () {
    },
    getVerification: function () {
        $.start()
        var data = {
            appid: popup_qr_appId,
            username: $('#J-userName').val().trim(),
            castNum: $("#id_card").val().trim()
        }
        $.ajax({
            url: popup_sms_verification,
            type: 'POST',
            data: data,
            success: function (response) {
                if (response) {
                    if (response.result_code == '0') {
                        $("#message p").html(response.result_message)
                    } else if (response.result_code == '6') {
                        $("#message p").html(response.result_message)
                    } else if (response.result_code == '11') {
                        $("#message p").html(response.result_message)
                    }
                    $("#message").show();
                    try {
                        setTimeout(function () {
                            $("#message").attr('aria-label', response.result_message);
                            $("#message").focus().css({
                                'border': '1px solid blue'
                            });
                        }, 1000);
                    } catch (e) {

                    }
                }
            },
            error: function (error) {
                $("#verification_code").removeClass('btn-disabled')
            }
        })
    },
    smsVerification: function () {
        // var reg = /(^\d{3}[0-9Xx]$)/
        $("#id_card").focus(function () {
            $("#message").hide();
        })
        $("#id_card").on('input', function () {
            if ($(this).val().trim().length == 4) {
                $("#verification_code").removeClass('btn-disabled')
            } else {
                $("#verification_code").addClass('btn-disabled')
            }
        })
        $("#verification_code").click(function () {
            $("#message").hide();
            var data = {
                appid: popup_qr_appId,
                username: $('#J-userName').val().trim(),
                castNum: $("#id_card").val().trim()
            }
            if (!$("#verification_code").hasClass('btn-disabled')) {
                $("#verification_code").addClass('btn-disabled')
                $.getVerification()
            }
        })
        $("#code").on('focus', function () {
            $("#message").hide();
        })
        // 短信确认
        $("#sureClick").off('click').click(function () {
            if ($("#id_card").val().trim() == '') {
                $("#message p").html('请输入登陆账号绑定的证件号后4位')
                $("#message").show();
                try {
                    setTimeout(function () {
                        $("#message").attr('aria-label', '请输入登陆账号绑定的证件号后4位');
                        $("#message").focus();
                    }, 1000);
                } catch (e) {

                }
                return false
            }
            if ($("#code").val().length != 6) {
                $("#message p").html('请输入正确验证码')
                $("#message").show();
                try {
                    setTimeout(function () {
                        $("#message").attr('aria-label', '请输入正确验证码');
                        $("#message").focus();
                    }, 1000);
                } catch (e) {

                }
                if ($("#code").val() == '') {
                    $("#message p").html('请输入验证码')
                    $("#message").show();
                    try {
                        setTimeout(function () {
                            $("#message").attr('aria-label', '请输入验证码');
                            $("#message").focus();
                        }, 1000);
                    } catch (e) {

                    }
                }
                return false
            } else {
                var formData = {};
                formData['sessionId'] = '';
                formData['sig'] = '';
                formData['if_check_slide_passcode_token'] = '';
                formData['scene'] = '';
                formData['checkMode'] = '0'
                formData['randCode'] = $("#code").val().trim()
                formData['username'] = $('#J-userName').val().trim();
                formData['password'] = '@' + encrypt_ecb($('#J-password').val().trim(), SM4_key);
                formData['appid'] = popup_passport_appId;
                if (popup_is_uam_login == 'Y') {
                    // 统一认证登录+短信验证
                    $.popup_loginForUam(formData);
                } else {
                    //本地登录+短信验证
                    $.popup_loginForLocation_passcode(formData);
                }

                $("#verification_code").addClass('btn-disabled')
            }
        })
    },
    // 判断是否为复制黏贴
    isPaste: function () {
        paste = 'Y'
    },
    // 国际手机号发送短信
    getAreaCode:function(param,ele) {
        $.ajax({
            url: sendMobileMsg,
            type: 'POST',
            timeout: 10000,
            dataType: 'json',
            data:param,
            success: function (result) {
                if (result.data&&result.status) {
                    $.countDown(ele)
                }else{
                    if(result.error){
                        var error = '<i class="icon icon-plaint-fill txt-error"></i>'+result.error
                        $("#mobile_msg_err").text(error).show();
                    }else{
                        $("#mobile_msg_err").show();
                    }
                } 
            },
            error: function (err) {
            }
        })
    },
    // 倒计时60s
    countDown:function(ele){
        if(flag){
            flag = false;
            timer = setInterval(function () {
                if(time == 0){
                    $(ele).html("获取验证码").css('color','rgb(59, 153, 252)');
                    $.clearTimer(ele)
                }else {
                    $(ele).html(time + " s 重新发送").css('color','rgb(128, 128, 128)');
                    time--;
                }
            },1000);
        }
    },
    // login_control_cancel
    clearTimer:function(ele){
        clearInterval(timer);
        time = 60;
        flag = true;
        $(ele).html("获取验证码").css('color','rgb(59, 153, 252)');
    }
});

