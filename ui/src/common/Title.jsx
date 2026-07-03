import React from 'react'
import { useMediaQuery } from '@material-ui/core'
import { useTranslate } from 'react-admin'
import { APP_NAME } from '../consts'

export const Title = ({ subTitle, args }) => {
  const translate = useTranslate()
  const isDesktop = useMediaQuery((theme) => theme.breakpoints.up('md'))
  const text = translate(subTitle, { ...args, _: subTitle })

  if (isDesktop) {
    return (
      <span>
        {APP_NAME} {text ? ` - ${text}` : ''}
      </span>
    )
  }
  return <span>{text ? text : APP_NAME}</span>
}
