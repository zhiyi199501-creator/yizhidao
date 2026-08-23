from typing import List

from app.config import settings
from app.errors import AppError


def _mask_phone(phone: str) -> str:
    return f"{phone[:3]}****{phone[-4:]}"


def deliver_sms_code(phone: str, code: str) -> None:
    # 避免循环导入：白名单判断放在调用方附近
    from app.services.auth import is_sms_test_phone

    if is_sms_test_phone(phone):
        print(f"[sms:test] phone={_mask_phone(phone)} code={code}")
        return

    provider = (settings.sms_provider or "mock").lower()
    if provider == "tencent":
        _send_tencent(phone, code)
        return
    if provider == "aliyun":
        _send_aliyun(phone)
        return
    if provider == "mock":
        print(f"[sms:mock] phone={_mask_phone(phone)} code={code}")
        return
    raise AppError(f"未知短信通道: {provider}", code=5000, status_code=500)


def _send_tencent(phone: str, code: str) -> None:
    missing = [
        name
        for name, value in [
            ("TENCENT_SECRET_ID", settings.tencent_secret_id),
            ("TENCENT_SECRET_KEY", settings.tencent_secret_key),
            ("TENCENT_SMS_SDK_APP_ID", settings.tencent_sms_sdk_app_id),
            ("TENCENT_SMS_SIGN_NAME", settings.tencent_sms_sign_name),
            ("TENCENT_SMS_TEMPLATE_ID", settings.tencent_sms_template_id),
        ]
        if not value
    ]
    if missing:
        raise AppError(
            f"腾讯云短信未配置：{', '.join(missing)}",
            code=5000,
            status_code=500,
        )

    try:
        from tencentcloud.common import credential
        from tencentcloud.common.exception.tencent_cloud_sdk_exception import (
            TencentCloudSDKException,
        )
        from tencentcloud.sms.v20210111 import models, sms_client
    except ImportError as exc:
        raise AppError(
            "未安装腾讯云 SDK，请执行: pip install tencentcloud-sdk-python",
            code=5000,
            status_code=500,
        ) from exc

    e164 = phone if phone.startswith("+") else f"+86{phone}"
    template_params = _tencent_template_params(code)

    try:
        cred = credential.Credential(
            settings.tencent_secret_id,
            settings.tencent_secret_key,
        )
        client = sms_client.SmsClient(cred, settings.tencent_sms_region)
        req = models.SendSmsRequest()
        req.SmsSdkAppId = settings.tencent_sms_sdk_app_id
        req.SignName = settings.tencent_sms_sign_name
        req.TemplateId = settings.tencent_sms_template_id
        req.TemplateParamSet = template_params
        req.PhoneNumberSet = [e164]
        resp = client.SendSms(req)
    except TencentCloudSDKException as exc:
        raise AppError(f"短信发送失败：{exc.message}", code=5000, status_code=502) from exc
    except Exception as exc:  # noqa: BLE001
        raise AppError("短信发送失败", code=5000, status_code=502) from exc

    status_set = getattr(resp, "SendStatusSet", None) or []
    if not status_set:
        raise AppError("短信发送失败：无返回状态", code=5000, status_code=502)

    first = status_set[0]
    code_status = getattr(first, "Code", "") or ""
    if code_status != "Ok":
        message = getattr(first, "Message", "") or code_status or "未知错误"
        raise AppError(f"短信发送失败：{message}", code=5000, status_code=502)

    print(f"[sms:tencent] phone={_mask_phone(phone)} sent ok")


def _tencent_template_params(code: str) -> List[str]:
    """
    默认按常见双变量模板：{1}=验证码 {2}=有效分钟。
    若模板只有一个变量，把 TENCENT_SMS_TEMPLATE_PARAM_MODE=code_only。
    """
    mode = (settings.tencent_sms_template_param_mode or "code_and_minutes").lower()
    if mode == "code_only":
        return [code]
    return [code, str(settings.sms_code_expire_min)]


def _aliyun_client():
    try:
        from alibabacloud_dypnsapi20170525.client import Client as DypnsClient
        from alibabacloud_tea_openapi import models as open_api_models
    except ImportError as exc:
        raise AppError(
            "未安装阿里云号码认证 SDK，请执行: pip install alibabacloud_dypnsapi20170525",
            code=5000,
            status_code=500,
        ) from exc
    config = open_api_models.Config(
        access_key_id=settings.aliyun_access_key_id,
        access_key_secret=settings.aliyun_access_key_secret,
        endpoint="dypnsapi.aliyuncs.com",
    )
    return DypnsClient(config)


def _send_aliyun(phone: str) -> None:
    missing = [
        name
        for name, value in [
            ("ALIYUN_ACCESS_KEY_ID", settings.aliyun_access_key_id),
            ("ALIYUN_ACCESS_KEY_SECRET", settings.aliyun_access_key_secret),
            ("ALIYUN_SMS_SIGN_NAME", settings.aliyun_sms_sign_name),
            ("ALIYUN_SMS_TEMPLATE_CODE", settings.aliyun_sms_template_code),
        ]
        if not value
    ]
    if missing:
        raise AppError(
            f"阿里云短信未配置：{', '.join(missing)}",
            code=5000,
            status_code=500,
        )

    try:
        from alibabacloud_dypnsapi20170525 import models as dypns_models

        client = _aliyun_client()
        req = dypns_models.SendSmsVerifyCodeRequest(
            phone_number=phone,
            sign_name=settings.aliyun_sms_sign_name,
            template_code=settings.aliyun_sms_template_code,
            template_param=settings.aliyun_sms_template_param,
            code_length=settings.aliyun_sms_code_length,
            valid_time=settings.aliyun_sms_valid_sec,
            code_type=1,
            interval=settings.sms_cooldown_sec,
            return_verify_code=False,
        )
        resp = client.send_sms_verify_code(req)
    except AppError:
        raise
    except Exception as exc:  # noqa: BLE001
        raise AppError(f"短信发送失败：{exc}", code=5000, status_code=502) from exc

    body = getattr(resp, "body", None)
    if body is None or getattr(body, "code", "") != "OK":
        msg = getattr(body, "message", "") if body is not None else "无返回"
        raise AppError(f"短信发送失败：{msg}", code=5000, status_code=502)

    print(f"[sms:aliyun] phone={_mask_phone(phone)} sent ok")


def verify_aliyun_code(phone: str, code: str) -> bool:
    try:
        from alibabacloud_dypnsapi20170525 import models as dypns_models

        client = _aliyun_client()
        req = dypns_models.CheckSmsVerifyCodeRequest(
            phone_number=phone,
            verify_code=code,
        )
        resp = client.check_sms_verify_code(req)
    except AppError:
        raise
    except Exception as exc:  # noqa: BLE001
        raise AppError(f"验证码核验失败：{exc}", code=5000, status_code=502) from exc

    body = getattr(resp, "body", None)
    if body is None or getattr(body, "code", "") != "OK":
        msg = getattr(body, "message", "") if body is not None else "无返回"
        raise AppError(f"验证码核验失败：{msg}", code=5000, status_code=502)

    model = getattr(body, "model", None)
    verify_result = getattr(model, "verify_result", "") if model is not None else ""
    return verify_result == "PASS"
